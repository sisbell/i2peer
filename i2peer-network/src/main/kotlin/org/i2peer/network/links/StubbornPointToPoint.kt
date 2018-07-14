package org.i2peer.network.links

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.*

/**
 * This link sends all previously sent messages periodically.
 */
class StubbornPointToPoint(private val fairLossLink: SendChannel<EventTask>) : Link() {

    private val sentCommunications: CopyOnWriteArraySet<CommunicationTask> = CopyOnWriteArraySet()

    val fixedRateTimer = fixedRateTimer(name = "timeout",
            initialDelay = 100, period = 30000) {
        async {
            timeout()
        }
    }

    /**
     * Sends all the previously sent sentCommunications to lower layer
     */
    private suspend fun timeout() {
        val sentCommunication = sentCommunications.iterator()
        while (sentCommunication.hasNext()) fairLossLink.send(sentCommunication.next())
    }

    override suspend fun deliver(communicationTask: CommunicationTask) {
        val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
        while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)

    }

    override suspend fun send(communicationTask: CommunicationTask) {
        println("Send stubborn link")
        fairLossLink.send(communicationTask)
        sentCommunications.add(communicationTask)
    }
}