package org.i2peer.network

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import org.i2peer.network.links.FairLossPointToPoint
import org.i2peer.network.links.Link
import org.i2peer.network.links.StubbornPointToPoint

fun perfectPointToPoint() = actor<EventTask>(CommonPool) {
    val stubbornActor = stubbornPointToPoint()
    stubbornActor.send(ChannelTask("Register", this.channel))

    val perfectLink = StubbornPointToPoint(stubbornActor)

    for (event in channel) {
        when (event) {
            is ChannelTask -> registerEvent(event, perfectLink)
            is CommunicationTask -> {
                when (event.name) {
                    "Send" -> perfectLink.send(event)
                    "Deliver" -> perfectLink.deliver(event)
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
                    "Send" -> fll.sendAsync(event)
                    "Deliver" -> fll.deliver(event)
                }
            }
        }
    }
}

fun registerEvent(event: ChannelTask, link: Link) {
    when (event.name) {
        "Register" -> link.registerForDelivery(event.channel)
        "Unregister" -> link.unregisterForDelivery(event.channel)
    }
}

fun registerChannel(channel: Channel<EventTask>, link: Link) {

}

fun stubbornPointToPoint() = actor<EventTask>(CommonPool) {
    val fflActor = fairLossPointToPoint()
    fflActor.send(ChannelTask("Register", this.channel))

    val spp = StubbornPointToPoint(fflActor)
    for (event in channel) {
        when (event) {
            is ChannelTask -> registerEvent(event, spp)
            is CommunicationTask -> {
                when (event.name) {
                    "Send" -> spp.send(event)
                    "Deliver" -> spp.deliver(event)
                }
            }
        }
    }
}