package org.i2peer.network.links

import kotlinx.coroutines.experimental.async
import org.i2peer.network.*
import org.i2peer.network.tor.TorContext

/**
 * This functions as a secretary as defined by Agha, This link sends the message once and provides
 * no guarantee of success
 */
class FairLossPointToPoint() : Link() {
    override suspend fun send(event: CommunicationTask) {
        println("Send fair loss link")
        sendAsync(event)
    }

    /**
     * Sends message to targetProcess. The targetProcess may be a remote targetProcess located on another node or a local
     * targetProcess located on the same node.
     */
    fun sendAsync(communicationTask: CommunicationTask) = async {
        //targetProcess is local - find actor/channel
        if (TorContext.localOnionAddress().contains(communicationTask.communicationsPacket.targetProcess.port)) {
            val channel = ActorRegistry.get(communicationTask.communicationsPacket.targetProcess.id)
            channel.forEach { it.send(communicationTask) }
        } else {//remote targetProcess
            ProcessChannel.send(communicationTask.communicationsPacket)
        }
    }

    /**
     * Delivers communicationsPacket task up the network stack. The communicationsPacket port must match the address of this local
     * targetProcess.
     */
    override suspend fun deliver(communicationTask: CommunicationTask) {
        LOG.info("Delivered communcations: ${communicationTask.communicationsPacket}")
        //TODO: need to verify signature before passing up the stack
        //communicationTask.communicationsPacket
        val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
        while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)
    }

    companion object {
        val LOG = loggerFor(javaClass)
    }
}