package org.i2peer.process

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor
import org.i2peer.network.*

class Ping {

    fun pingActor() = actor<EventTask>(CommonPool) {
        fairLossPointToPoint().send(ChannelTask(REGISTER, channel))
        for (event in channel) {
            when (event) {
                is CommunicationTask -> {
                    //validate deliver=event.name
                    println("Pinged from ${event.communications.sourcePort}")

                    when (event.name) {
                       // DELIVER -> spp.deliver(event)
                    }
                }            }
            println("Pinged")
        }
    }

    //contruct network stack
    //register with fair loss
}