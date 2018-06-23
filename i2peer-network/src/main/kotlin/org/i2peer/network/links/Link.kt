package org.i2peer.network.links

import kotlinx.coroutines.experimental.channels.Channel
import org.i2peer.network.EventTask
import java.util.concurrent.CopyOnWriteArraySet

open class Link {

    protected val deliveryChannels: CopyOnWriteArraySet<Channel<EventTask>> = CopyOnWriteArraySet()

    fun registerForDelivery(channel: Channel<EventTask>) = deliveryChannels.add(channel)

    fun unregisterForDelivery(channel: Channel<EventTask>) = deliveryChannels.remove(channel)

}