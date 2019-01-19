package org.i2peer.network

import com.google.common.collect.Lists
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import okio.Buffer
import org.i2peer.network.links.*
import java.net.Socket

const val SEND = "Send"
const val DELIVER = "Deliver"
const val REGISTER = "Register"
const val UNREGISTER = "Unregister"

/**
 * Wrap the [communicationsPacket] in a [CommunicationTask] of type SEND and then addActorChannel the task to the channel. This does
 * not send the message to the endpoint but just adds it to a queue or channel for later processing.
 */
suspend fun SendChannel<EventTask>.sendCommunications(communicationsPacket: CommunicationsPacket): SendChannel<EventTask> {
    send(element = CommunicationTask(SEND, communicationsPacket))
    return this
}

/**
 * Wrap the [communicationsPacket] in a [CommunicationTask] of type DELIVER and then addActorChannel the task to the channel.
 * This does not deliver the message to a link but just adds it to a queue or channel for later processing.
 */
suspend fun SendChannel<EventTask>.deliver(communicationsPacket: CommunicationsPacket): SendChannel<EventTask> {
    println("SendChannel.deliver")
    send(element = CommunicationTask(DELIVER, communicationsPacket))
    return this
}

/**
 * Wrap the [deliveryChannel] in a [ChannelTask] of type REGISTER and then addActorChannel the task to the channel.
 *
 * Every actor has its own channel and will use this method to register other actor channels. This creates
 * the communication links between actors.
 */
suspend fun SendChannel<EventTask>.registerChannel(deliveryChannel: DeliveryChannel): SendChannel<EventTask> {
    send(element = ChannelTask(REGISTER, deliveryChannel))
    return this
}

/**
 * Wrap the [deliveryChannel] in a [ChannelTask] of type UNREGISTER and then addActorChannel the task to the channel.
 */
suspend fun SendChannel<EventTask>.unregisterChannel(deliveryChannel: DeliveryChannel): SendChannel<EventTask> {
    send(element = ChannelTask(UNREGISTER, deliveryChannel))
    return this
}

/**
 * Registers or unregisters [channelTask] on the specified [link]
 */
fun registerChannelTask(channelTask: ChannelTask, link: Link) {
    when (channelTask.name) {
        REGISTER -> link.registerForDelivery(channelTask.deliveryChannel)
        UNREGISTER -> link.unregisterForDelivery(channelTask.deliveryChannel)
    }
}

/**
 * Routes channel events to the specified link. This is the event loop that will process messages for an actor.
 * Supported events include [ChannelTask] and [CommunicationTask]
 */
suspend fun Channel<EventTask>.routeEventsToLink(link: Link) {
    println("Route to link. Waiting for events... ")
    for (eventTask in this) {
        when (eventTask) {
            is ChannelTask -> registerChannelTask(eventTask, link)
            is CommunicationTask -> {
                when (eventTask.name) {
                    SEND -> link.send(eventTask)
                    DELIVER -> link.deliver(eventTask)
                }
            }
        }
    }
}

/**
 * Registers handler to deliver events from specified [actor] to this channel. Only communication tasks that match
 * the list of [matchers] will be sent.
 */
suspend fun Channel<EventTask>.deliverEventsFrom(
    actor: SendChannel<EventTask>,
    matchers: List<CommunicationTaskMatcher>
)
        : SendChannel<EventTask> = actor.registerChannel(DeliveryChannel(this, matchers))

/**
 * Creates an actor that receives events from a [FairLossPointToPoint] link and routes those events to a [StubbornPointToPoint] link.
 * This actor will deliver all messages periodically. [fromChannel] is the actor below this one in the network stack.
 */
@ObsoleteCoroutinesApi
fun stubbornPointToPoint(
    fromChannel: SendChannel<EventTask>,
    pollPeriod: Long,
    matchers: List<CommunicationTaskMatcher> = Lists.newArrayList()
) = GlobalScope.actor<EventTask> {
    val fromActor =
        channel.deliverEventsFrom(
            fromChannel,
            matchers
        )
    //fairLossPointToPoint(processChannelActor(networkContext), networkContext)
    channel.routeEventsToLink(StubbornPointToPoint(fromActor, pollPeriod))
}

/**
 * Creates an actor that receives events from a [StubbornPointToPoint] link and routes these events to a [PerfectPointToPoint] link.
 * This actor will guarantee delivery of the message once.
 *
 * What happens here is that the [StubbornPointToPoint] link will keep delivering message M1 over and over. The
 * [PerfectPointToPoint] link will stop sending the message M1 once it has confirmed delivery one time.
 */
@ObsoleteCoroutinesApi
fun perfectPointToPoint(
    fromChannel: SendChannel<EventTask>, matchers: List<CommunicationTaskMatcher> = Lists.newArrayList()
) = GlobalScope.actor<EventTask> {
    val fromActor =
        channel.deliverEventsFrom(fromChannel, matchers)
    channel.routeEventsToLink(PerfectPointToPoint(fromActor))
}

/**
 * Interface between network stack and the application
 */
@ObsoleteCoroutinesApi
fun applicationLink(
    fromChannel: SendChannel<EventTask>) = GlobalScope.actor<EventTask> {
    val fromActor =
        channel.deliverEventsFrom(fromChannel, Lists.newArrayList(AnyCommunicationTaskMatcher()))
    channel.routeEventsToLink(ApplicationLayer(fromActor))
}

@ObsoleteCoroutinesApi
fun ping(
    fromChannel: SendChannel<EventTask>, matchers: List<CommunicationTaskMatcher> = Lists.newArrayList()
) = GlobalScope.actor<EventTask> {
    val fromActor =
        channel.deliverEventsFrom(fromChannel, matchers)
    channel.routeEventsToLink(Ping(fromActor))
}

/**
 * Creates an actor that routes its events to a [FairLossPointToPoint] link. This actor will send the message once
 * with no guarantee of delivery. [fromChannel] is a link from the remote network client
 */
@ObsoleteCoroutinesApi
fun fairLossPointToPoint(fromChannel: SendChannel<CommunicationsPacket>, networkContext: NetworkContext) =
    GlobalScope.actor<EventTask> {
        channel.routeEventsToLink(FairLossPointToPoint(fromChannel, networkContext))
    }

/**
 * Actor that sends [CommunicationsPacket]s to remote processes
 */
@ObsoleteCoroutinesApi
fun processChannelActor(networkContext: NetworkContext) = GlobalScope.actor<CommunicationsPacket> {
    for (communications in channel) {
        async {
            val processChannel =
                getChannelFor(targetPort = communications.targetProcess.port, networkContext = networkContext)
            processChannel?.sendCommunications(communications)
        }
    }
}

/**
 * Looks up existing process channel that is open or creates a new one
 */
@Synchronized
fun getChannelFor(targetPort: String, networkContext: NetworkContext)
        : ProcessChannel? {
    val channelMap = networkContext.channelMap
    var channel: ProcessChannel? = channelMap[targetPort]
    if (channel == null || !channel.isOpen() || !channel.isConnected) {
        try {
            println("Connecting to Tor Socks Port: ${networkContext.torConfig.socksPort}")
            channel = Socket().openProcessChannel(
                "127.0.0.1", networkContext.torConfig.socksPort, 5000,
                targetPort
            )
            channelMap[targetPort] = channel
        } catch (e: Exception) {
            e.printStackTrace()
            println("Unable to open the process channel: Creating dummy channel - messages will not be sent. ${e.message}")
            channel = ProcessChannel(Buffer(), Buffer(), false)
            channelMap[targetPort] = channel
        }
    }
    return channel
}