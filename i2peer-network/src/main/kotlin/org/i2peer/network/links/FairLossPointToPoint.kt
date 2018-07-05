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
     * Sends message to targetProcess. The targetProcess may be a remote targetProcess located on another node or a local
     * targetProcess located on the same node.
     */
    fun sendAsync(communicationTask: CommunicationTask) = async {
        //targetProcess is local - find actor/channel
        if (TorContext.localOnionAddress().contains(communicationTask.communications.targetProcess.port)) {
            val channel = ActorRegistry.get(communicationTask.communications.targetProcess.id)
            channel.forEach { it.send(communicationTask) }
        } else {//remote targetProcess
            ProcessChannel.send(communicationTask.communications)
        }
    }

    /**
     * Delivers communications task up the network stack. The communications port must match the address of this local
     * targetProcess.
     */
    suspend fun deliver(communicationTask: CommunicationTask) {
        LOG.info("Delivered communcations: ${communicationTask.communications}")
        val deliveryChannel = deliveryChannels.iterator()
        while (deliveryChannel.hasNext()) deliveryChannel.next().send(communicationTask)
    }

    companion object {
        val LOG = loggerFor(javaClass)
    }
}