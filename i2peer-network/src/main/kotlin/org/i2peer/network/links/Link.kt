package org.i2peer.network.links

import org.i2peer.network.CommunicationTask
import org.i2peer.network.DeliveryChannel
import java.util.concurrent.CopyOnWriteArraySet

abstract class Link {

    protected val deliveryChannels: CopyOnWriteArraySet<DeliveryChannel> = CopyOnWriteArraySet()

    abstract suspend fun deliver(communicationTask: CommunicationTask)

    abstract suspend fun send(communicationTask: CommunicationTask)

    fun registerForDelivery(channel: DeliveryChannel) = deliveryChannels.add(channel)

    fun unregisterForDelivery(channel: DeliveryChannel) = deliveryChannels.remove(channel)

}