package org.i2peer.network

import arrow.core.Either
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.actor
import okio.BufferedSink
import okio.BufferedSource
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * A communication channel to a specific Process.
 *
 * A Process is an abstraction that can perform some computation. Processes pass messages between themselves through
 * communication links.
 *
 * More information can be found in "Introduction of Reliable and Secure Distributed Programming 2nd edition (pg 20)
 */
class ProcessChannel(val source: BufferedSource, val sink: BufferedSink) {

    /**
     * Sends a communication message to the specified sink.
     */
    fun sendCommunications(communicationsPacket: CommunicationsPacket) {
        async {
            sink.writeCommunications(communicationsPacket);//.writeSignature().flush()
        }
    }

    /**
     * Returns true if the channel is closed and can no longer be used
     */
    fun isOpen(): Boolean = false//source.isOpen && sink.isOpen

    companion object {

        val LOG = loggerFor(javaClass)

        /**
         * Sends a communication message to the onion address specified in the CommunicationsPacket.targetProcess.port
         */
        suspend fun send(communicationsPacket: CommunicationsPacket) = peerActor.send(communicationsPacket)

        private var peerActor = actor<CommunicationsPacket>(CommonPool) {
            val channelMap: MutableMap<String, ProcessChannel> = ConcurrentHashMap()
            for (communications in channel) {
                async {
                    val result = getChannelFor(channelMap = channelMap, targetPort = communications.targetProcess.port)
                    when (result) {
                        is Either.Left -> result.a.printStackTrace()
                        is Either.Right -> result.b.sendCommunications(communications)
                    }
                }
            }
        }

        @Synchronized
        private fun getChannelFor(targetPort: String, channelMap: MutableMap<String, ProcessChannel>)
                : Either<Exception, ProcessChannel> {
            var channel = channelMap[targetPort]
            if (channel == null || !channel.isOpen()) {
                try {
                    LOG.info("Connecting to Tor Socks Port: " + org.i2peer.network.config.socksPort)
                    channel = Socket().openProcessChannel("localhost", org.i2peer.network.config.socksPort, 5000,
                            targetPort)
                    channelMap[targetPort] = channel
                } catch (e: Exception) {
                    e.printStackTrace()
                    return Either.left(e)
                }
            }
            return Either.right(channel)
        }
    }
}
