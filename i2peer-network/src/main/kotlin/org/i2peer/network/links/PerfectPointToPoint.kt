package org.i2peer.network.links

import kotlinx.coroutines.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet

/**
 * This link guarantees a message is delivered once. This class instance keeps a record of all delivered messages and if
 * it has delivered a message previously, it will not redeliver it.
 */
class PerfectPointToPoint(private val stubbornLink: SendChannel<EventTask>) : Link() {

    private val delivered: CopyOnWriteArraySet<CommunicationTask> = CopyOnWriteArraySet()

    /**
     * Delivers [communicationTask] once
     */
    override suspend fun deliver(communicationTask: CommunicationTask) {
        println("P2P.deliver")
        if (!delivered.contains(communicationTask)) {
            println("P2P.deliver - first delivery: ${communicationTask}")
            delivered.add(communicationTask)
            val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
            while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)
        }
    }

    /**
     * Sends the [communicationTask] to a [StubbornPointToPoint] link
     */
    override suspend fun send(communicationTask: CommunicationTask) {
        println("Send PerfectLink: ${communicationTask.communicationsPacket.message}")
        stubbornLink.send(communicationTask)
    }

}