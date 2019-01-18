package org.i2peer.network.links

import org.i2peer.network.CommunicationTask
import org.i2peer.network.DeliveryChannel
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Provides connectivity between a pair of processes.
 */
abstract class Link {

    /**
     * This link's set of delivery channels that will be used to pass messages up the network stack to the consuming app
     */
    protected val deliveryChannels: CopyOnWriteArraySet<DeliveryChannel> = CopyOnWriteArraySet()

    /**
     * Delivers [communicationsTask] up the network stack. The communicationsPacket port must match the address of this local
     * targetProcess. Otherwise, it will be discarded.
     */
    abstract suspend fun deliver(communicationTask: CommunicationTask)

    /**
     * Sends a [communicationTask] which may be to a local or remote node.
     */
    abstract suspend fun send(communicationTask: CommunicationTask)

    /**
     * Registers a [channel] for this link. Based on the rules of the link, it will deliver communication packets it
     * receives to these registered channels.
     */
    fun registerForDelivery(channel: DeliveryChannel) = deliveryChannels.add(channel)

    /**
     * Unregisters a [channel]
     */
    fun unregisterForDelivery(channel: DeliveryChannel) = deliveryChannels.remove(channel)

}