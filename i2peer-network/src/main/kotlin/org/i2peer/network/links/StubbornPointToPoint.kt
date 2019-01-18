package org.i2peer.network.links

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.fixedRateTimer

/**
 * This link sends all previously sent messages periodically.
 */
class StubbornPointToPoint(private val fairLossLink: SendChannel<EventTask>, pollPeriod: Long) : Link() {

    private val sentCommunications: CopyOnWriteArraySet<CommunicationTask> = CopyOnWriteArraySet()

    /**
     * Periodically send previously cached messaged
     */
    val fixedRateTimer = fixedRateTimer(
        name = "timeout",
        initialDelay = 100, period = pollPeriod
    ) {
        GlobalScope.async {
            timeout()
        }
    }

    /**
     * On timeout, sends all the previously sent communications to a [FairLossPointToPoint] link. It doesn't care
     * if the endpoint ultimately receives the message, it will just keep sending it.
     */
    private suspend fun timeout() {
        val sentCommunication = sentCommunications.iterator()
        while (sentCommunication.hasNext()) fairLossLink.send(sentCommunication.next())
    }

    override suspend fun deliver(communicationTask: CommunicationTask) {
        println("SP2P. Deliver: " + deliveryChannels.size)
        val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
        while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)
    }

    /**
     * Sends the [CommunicationTask] to a [FairLossPointToPoint] link and caches the messages.
     */
    override suspend fun send(communicationTask: CommunicationTask) {
        fairLossLink.send(communicationTask)
        sentCommunications.add(communicationTask)
    }
}