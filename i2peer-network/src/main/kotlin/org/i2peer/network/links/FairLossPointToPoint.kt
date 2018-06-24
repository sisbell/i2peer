package org.i2peer.network.links

import kotlinx.coroutines.experimental.async
import org.i2peer.network.*
import org.i2peer.network.tor.TorContext

/**
 * This functions as a secretary as defined by Agha, This link sends the message once and provides
 * no guarantee of success
 */
class FairLossPointToPoint() : Link() {

    /**
     * Sends message to process. The process may be a remote process located on another node or a local
     * process located on the same node.
     */
    fun sendAsync(communicationTask: CommunicationTask) = async {
        //process is local - find actor/channel
        if(TorContext.localOnionAddress().contains(communicationTask.communications.process.port)) {
            val channel = ActorRegistry.get(communicationTask.communications.process.id)
            channel.forEach { it.send(communicationTask) }
        } else {//remote process
            ProcessChannel.send(communicationTask.communications)
        }
    }

    suspend fun deliver(event: CommunicationTask) {
        val deliveryChannel = deliveryChannels.iterator()
        while(deliveryChannel.hasNext()) deliveryChannel.next().send(event)
    }
}