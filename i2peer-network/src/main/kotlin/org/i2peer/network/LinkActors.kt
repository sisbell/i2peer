package org.i2peer.network

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import org.i2peer.network.links.FairLossPointToPoint
import org.i2peer.network.links.Link
import org.i2peer.network.links.StubbornPointToPoint

val SEND = "Send"
val DELIVER = "Deliver"
val REGISTER = "Register"

suspend fun SendChannel<EventTask>.sendCommunications(communications: Communications) :  SendChannel<EventTask> {
    send(element = CommunicationTask(SEND, communications))
    return this
}

suspend fun SendChannel<EventTask>.deliver(communications: Communications) :  SendChannel<EventTask> {
    send(element = CommunicationTask(DELIVER, communications))
    return this
}

suspend fun SendChannel<EventTask>.registerChannel(channel: Channel<EventTask>) : SendChannel<EventTask> {
    send(element = ChannelTask(REGISTER, channel))
    return this
}

suspend fun SendChannel<EventTask>.unregisterChannel(channel: Channel<EventTask>) :  SendChannel<EventTask>  {
    send(element = ChannelTask("Unregister", channel))
    return this
}

fun perfectPointToPoint() = actor<EventTask>(CommonPool) {
    val stubbornActor = stubbornPointToPoint().registerChannel(this.channel)
    val stubbornPointToPoint = StubbornPointToPoint(stubbornActor)

    for (event in channel) {
        when (event) {
            is ChannelTask -> registerEvent(event, stubbornPointToPoint)
            is CommunicationTask -> {
                when (event.name) {
                    SEND -> stubbornPointToPoint.send(event)
                    DELIVER -> stubbornPointToPoint.deliver(event)
                }
            }
        }
    }
}

fun fairLossPointToPoint() = actor<EventTask>(CommonPool) {
    val fll = FairLossPointToPoint()
    for (event in channel) {
        when (event) {
            is ChannelTask -> registerEvent(event, fll)
            is CommunicationTask -> {
                when (event.name) {
                    SEND -> fll.sendAsync(event)
                    DELIVER -> fll.deliver(event)
                }
            }
        }
    }
}

fun registerEvent(event: ChannelTask, link: Link) {
    when (event.name) {
        REGISTER -> link.registerForDelivery(event.channel)
        "Unregister" -> link.unregisterForDelivery(event.channel)
    }
}

fun stubbornPointToPoint() = actor<EventTask>(CommonPool) {
    val fflActor = fairLossPointToPoint().registerChannel(channel)

    val spp = StubbornPointToPoint(fflActor)
    for (event in channel) {
        when (event) {
            is ChannelTask -> registerEvent(event, spp)
            is CommunicationTask -> {
                when (event.name) {
                    SEND -> spp.send(event)
                    DELIVER -> spp.deliver(event)
                }
            }
        }
    }
}