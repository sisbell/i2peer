package org.i2peer.network

import com.google.common.collect.Lists
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import org.i2peer.network.links.FairLossPointToPoint
import org.i2peer.network.links.Link
import org.i2peer.network.links.PerfectPointToPoint
import org.i2peer.network.links.StubbornPointToPoint

val SEND = "Send"
val DELIVER = "Deliver"
val REGISTER = "Register"
val UNREGISTER = "Unregister"

suspend fun SendChannel<EventTask>.sendCommunications(communicationsPacket: CommunicationsPacket): SendChannel<EventTask> {
    send(element = CommunicationTask(SEND, communicationsPacket))
    return this
}

suspend fun SendChannel<EventTask>.deliver(communicationsPacket: CommunicationsPacket): SendChannel<EventTask> {
    println("SendChannel.deliver")
    send(element = CommunicationTask(DELIVER, communicationsPacket))
    return this
}

suspend fun SendChannel<EventTask>.registerChannel(deliveryChannel: DeliveryChannel): SendChannel<EventTask> {
    send(element = ChannelTask(REGISTER, deliveryChannel))
    return this
}

suspend fun SendChannel<EventTask>.unregisterChannel(deliveryChannel: DeliveryChannel): SendChannel<EventTask> {
    send(element = ChannelTask(UNREGISTER, deliveryChannel))
    return this
}

fun registerEvent(channelTask: ChannelTask, link: Link) {
    when (channelTask.name) {
        REGISTER -> link.registerForDelivery(channelTask.deliveryChannel)
        UNREGISTER -> link.unregisterForDelivery(channelTask.deliveryChannel)
    }
}

/**
 * Routes channel events to the specified link
 */
suspend fun Channel<EventTask>.routeEventsTo(link: Link) {
    for (event in this) {
        when (event) {
            is ChannelTask -> registerEvent(event, link)
            is CommunicationTask -> {
                when (event.name) {
                    SEND -> link.send(event)
                    DELIVER -> link.deliver(event)
                }
            }
        }
    }
}

/**
 * Deliver events from specified actor to this channel
 */
suspend fun Channel<EventTask>.deliverEventsFrom(actor: SendChannel<EventTask>, matchers: List<CommunicationTaskMatcher>)
        : SendChannel<EventTask> = actor.registerChannel(DeliveryChannel(this, matchers))

fun stubbornPointToPoint() = actor<EventTask>(CommonPool) {
    val fairLossActor = channel.deliverEventsFrom(fairLossPointToPoint(), Lists.newArrayList())
    channel.routeEventsTo(StubbornPointToPoint(fairLossActor))
}

fun perfectPointToPoint() = actor<EventTask>(CommonPool) {
    val stubbornActor = channel.deliverEventsFrom(stubbornPointToPoint(), Lists.newArrayList())
    channel.routeEventsTo(PerfectPointToPoint(stubbornActor))
}

fun fairLossPointToPoint() = actor<EventTask>(CommonPool) {
    channel.routeEventsTo(FairLossPointToPoint())
}

