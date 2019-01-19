package org.i2peer.network.links

import kotlinx.coroutines.channels.SendChannel
import org.i2peer.auth.NoAuthInfo
import org.i2peer.network.*

class Ping(private val lowerLayer: SendChannel<EventTask>) : Link() {

    override suspend fun deliver(communicationTask: CommunicationTask) {
        val cp = communicationTask.communicationsPacket
        if (cp.message.type == 303) {
            println("PING: ${communicationTask}")
            val targetProcess = Process("myId", cp.sourcePort, "ping")
            val message = Message(304, "Pong".toByteArray())
            val compack =
                CommunicationsPacket(
                    CodeGenerator.generateCode(), cp.sourcePacketId, cp.targetProcess.port, targetProcess,
                    NoAuthInfo(), System.currentTimeMillis(), message
                )
            val returnTask = CommunicationTask(SEND, compack)
            send(returnTask)
        } else if(cp.message.type == 304) {
            println("PONG: ${communicationTask}")
        }
    }

    override suspend fun send(communicationTask: CommunicationTask) {
        lowerLayer.send(communicationTask)
    }
}