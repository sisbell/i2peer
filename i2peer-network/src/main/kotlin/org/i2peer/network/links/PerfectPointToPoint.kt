package org.i2peer.network.links

import kotlinx.coroutines.experimental.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet

/**
 * This link guarantees a message is delivered once
 */
class PerfectPointToPoint(private val stubbornLink: SendChannel<EventTask>) : Link() {

    private val delivered: CopyOnWriteArraySet<CommunicationTask> = CopyOnWriteArraySet()

    /**
     * Delivers event once
     */
    override suspend fun deliver(communicationTask: CommunicationTask) {
        println("Deliver PerfectLink: " + communicationTask.communicationsPacket.message)
        if (!delivered.contains(communicationTask)) {
            delivered.add(communicationTask)
            val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
            while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)
        }
    }

    override suspend fun send(event: CommunicationTask) {
        println("Send PerfectLink: " + event.communicationsPacket.message)
        stubbornLink.send(event)
    }

}