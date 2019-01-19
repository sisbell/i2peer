package org.i2peer.network

import com.google.common.collect.Lists
import kotlinx.coroutines.channels.SendChannel
import org.i2peer.auth.NoAuthInfo
import org.i2peer.network.links.Link
import java.util.concurrent.ConcurrentHashMap

/**
 * Layer 7 interface into the application layer
 */
class ApplicationLayer(private val lowerLayer: SendChannel<EventTask>) : Link() {

    interface MessageListener {
        suspend fun deliver(sourceProcess: Process, authInfo: AuthInfo, message: Message)
    }

    /**
     * This registered listener is similar to typical REST or Web endpoints. It listens to a [Process.path].
     *
     * The listener implementation will be responsible to determining if this event is a request or response. This can
     * be done by looking at the [CommunicationsPacket.responsePacketId]. In the case of a request, the listener will
     * need to look at the [CommunicationsPacket.sourcePort] and send the response back through
     * [ApplicationLayer.sendMessage]
     */
    fun registerMessageListener(messageListener: MessageListener, processPath: String) {
        listeners.put(messageListener, Lists.newArrayList(PathPacketMatcher(processPath)))
    }

    fun registerMessageListener(messageListener: MessageListener, matchers: List<CommunicationPacketMatcher>) {
        listeners.put(messageListener, matchers)
    }

    fun unregisterMessageListener(messageListener: MessageListener) {
        listeners.remove(messageListener)
    }

    private val listeners: ConcurrentHashMap<MessageListener, List<CommunicationPacketMatcher>> =
        ConcurrentHashMap()

    /**
     * Send a message to the [targetProcess]. If this is a response to a previously sent message, then include the
     * [responsePacketId], otherwise leave it blank
     */
    suspend fun sendMessage(
        targetProcess: Process,
        authInfo: AuthInfo = NoAuthInfo(),
        message: Message,
        responsePacketId: String = ""
    ) {
        //TODO: need to look up sourcePort - the onionAddress of this service
        send(
            CommunicationTask(
                SEND, CommunicationsPacket(
                    CodeGenerator.generateCode(), responsePacketId, "", targetProcess,
                    authInfo, System.currentTimeMillis(), message
                )
            )
        )
    }

    override suspend fun deliver(communicationTask: CommunicationTask) {
        val cp = communicationTask.communicationsPacket
        listeners.forEach() {
            if (it.value.all { it.match(cp) }) {
                it.key.deliver(cp.targetProcess, cp.authInfo, cp.message)
            }
        }
    }

    override suspend fun send(communicationTask: CommunicationTask) {
        lowerLayer.send(communicationTask)
    }
}