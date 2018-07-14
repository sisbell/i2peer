package org.i2peer.network

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor

fun pingActor() = actor<EventTask>(CommonPool) {
    fairLossPointToPoint().registerChannel(DeliveryChannel(channel,
            listOf(EndpointMatcher("i2peer://ping"))))
    for (event in channel) {
        when (event) {
            is CommunicationTask -> {
                //validate deliver=event.name
                println("Pinged from ${event.communicationsPacket.sourcePort}")

                when (event.name) {
                // DELIVER -> spp.deliver(event)
                }
            }            }
        println("Pinged")
    }
}