package org.i2peer.network.links

import kotlinx.coroutines.experimental.async
import org.i2peer.network.ActorRegistry
import org.i2peer.network.CommunicationTask
import org.i2peer.network.Message
import org.i2peer.network.Process

/**
 * This functions as a secretary as defined by Agha, This link sends the message once and provides
 * no guarantee of success
 */
class FairLossPointToPoint() : Link() {

    /**
     * Sends message to process. The process may be a remote process located on another node or a local
     * process located on the same node.
     */
    fun sendAsync(process: Process, message: Message) = async {
        //lookup process
        //if process is remote - find network address

        //if process is local - find actor/channel
        val channel = ActorRegistry.get(process.id)
        channel.forEach { it.send(message) }//EventTask????
    }

    suspend fun deliver(event: CommunicationTask) {
        val deliveryChannel = deliveryChannels.iterator()
        while(deliveryChannel.hasNext()) deliveryChannel.next().send(event)
    }
}