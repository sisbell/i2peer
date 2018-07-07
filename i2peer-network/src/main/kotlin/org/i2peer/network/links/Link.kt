package org.i2peer.network.links

import kotlinx.coroutines.experimental.channels.Channel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet

abstract class Link {

    protected val deliveryChannels: CopyOnWriteArraySet<Channel<EventTask>> = CopyOnWriteArraySet()

    abstract suspend fun deliver(event: CommunicationTask)

    abstract suspend fun send(event: CommunicationTask)

    fun registerForDelivery(channel: Channel<EventTask>) = deliveryChannels.add(channel)

    fun unregisterForDelivery(channel: Channel<EventTask>) = deliveryChannels.remove(channel)

}