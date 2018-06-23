package org.i2peer.network.links

import kotlinx.coroutines.experimental.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet

/**
 * This link guarantees a message is delivered once
 */
class PerfectPointToPoint(private val stubbornLink: SendChannel<EventTask>) : Link() {

    protected val delivered: CopyOnWriteArraySet<CommunicationTask> = CopyOnWriteArraySet()

    /**
     * Delivers event once
     */
    suspend fun deliver(event: CommunicationTask) {
        if(!delivered.contains(event)) {
            delivered.add(event)
            val deliveryChannel = deliveryChannels.iterator()
            while(deliveryChannel.hasNext()) deliveryChannel.next().send(event)
        }
    }

    suspend fun send(event: CommunicationTask) {
        stubbornLink.send(event)
    }

}