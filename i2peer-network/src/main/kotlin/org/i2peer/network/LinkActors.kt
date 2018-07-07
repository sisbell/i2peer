package org.i2peer.network

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

suspend fun SendChannel<EventTask>.sendCommunications(communications: Communications): SendChannel<EventTask> {
    send(element = CommunicationTask(SEND, communications))
    return this
}

suspend fun SendChannel<EventTask>.deliver(communications: Communications): SendChannel<EventTask> {
    send(element = CommunicationTask(DELIVER, communications))
    return this
}

suspend fun SendChannel<EventTask>.registerChannel(channel: Channel<EventTask>): SendChannel<EventTask> {
    send(element = ChannelTask(REGISTER, channel))
    return this
}

suspend fun SendChannel<EventTask>.unregisterChannel(channel: Channel<EventTask>): SendChannel<EventTask> {
    send(element = ChannelTask(UNREGISTER, channel))
    return this
}

fun registerEvent(event: ChannelTask, link: Link) {
    when (event.name) {
        REGISTER -> link.registerForDelivery(event.channel)
        UNREGISTER -> link.unregisterForDelivery(event.channel)
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
suspend fun Channel<EventTask>.deliverEventsFrom(actor: SendChannel<EventTask>): Channel<EventTask> {
    actor.registerChannel(this)
    return this
}

fun stubbornPointToPoint() = actor<EventTask>(CommonPool) {
    val fairLossActor = channel.deliverEventsFrom(fairLossPointToPoint())
    channel.routeEventsTo(StubbornPointToPoint(fairLossActor))
}

fun perfectPointToPoint() = actor<EventTask>(CommonPool) {
    val stubbornActor = channel.deliverEventsFrom(stubbornPointToPoint())
    channel.routeEventsTo(PerfectPointToPoint(stubbornActor))
}

fun fairLossPointToPoint() = actor<EventTask>(CommonPool) {
    channel.routeEventsTo(FairLossPointToPoint())
}

