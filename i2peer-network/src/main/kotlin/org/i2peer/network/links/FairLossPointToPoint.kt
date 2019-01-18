package org.i2peer.network.links

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import org.i2peer.network.CommunicationTask
import org.i2peer.network.CommunicationsPacket
import org.i2peer.network.NetworkContext

/**
 * This functions as a secretary as defined by Agha, This link sends the message once and provides no guarantee of success
 */
class FairLossPointToPoint(val remoteChannel: SendChannel<CommunicationsPacket>, val networkContext: NetworkContext) :
    Link() {

    /**
     * Sends [communicationTask] to its targetProcess. The targetProcess may be a remote targetProcess located on another
     * node or a local targetProcess located on the same node.
     *
     * A local node is defined as a node where its port is the same as the local onion address. In this case, the message
     * is sent in-memory and not across the network.
     */
    @ObsoleteCoroutinesApi
    override suspend fun send(communicationTask: CommunicationTask) {
        GlobalScope.async {
            //targetProcess is local - find actor/channel
            if (isLocalAddress(communicationTask)) {
                val channel = networkContext.getActorChannels(communicationTask.communicationsPacket.targetProcess.id)
                channel.forEach { it.send(communicationTask) }
            } else {//remote targetProcess
                println("Send message to remote target")
                remoteChannel.send(communicationTask.communicationsPacket)
            }
        }
    }

    /**
     * Delivers [communicationsTask] up the network stack. The communicationsPacket port must match the address of this
     * local targetProcess.
     */
    override suspend fun deliver(communicationTask: CommunicationTask) {
        println("FLP.Delivered communcations: ${communicationTask.communicationsPacket} ${deliveryChannels.size}")
        //TODO: need to verify signature before passing up the stack
        //   if (isLocalAddress(communicationTask)) {
        val deliveryChannel = deliveryChannels.filter { it.match(communicationTask) }.iterator()
        while (deliveryChannel.hasNext()) deliveryChannel.next().channel.send(communicationTask)
        //  }
    }

    /**
     * Is this [communicationTask] addressed to one of our hidden services?
     */
    private fun isLocalAddress(communicationTask: CommunicationTask): Boolean =
        networkContext.localOnionAddress.contains(communicationTask.communicationsPacket.targetProcess.port)
}