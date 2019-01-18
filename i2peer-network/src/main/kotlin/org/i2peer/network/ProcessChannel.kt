package org.i2peer.network

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import okio.BufferedSink
import okio.BufferedSource

/**
 * A communication channel to a specific Process.
 *
 * A Process is an abstraction that can perform some computation. Processes pass messages between themselves through
 * communication links.
 *
 * More information can be found in "Introduction of Reliable and Secure Distributed Programming 2nd edition (pg 20)
 */
class ProcessChannel(val source: BufferedSource, val sink: BufferedSink, val isConnected: Boolean) {

    /**
     * Sends a [communicationsPacket] to the specified [sink] of this ProcessChannel.
     */
    fun sendCommunications(communicationsPacket: CommunicationsPacket): Deferred<BufferedSink> {
        return GlobalScope.async {
            val buffer = sink.writeCommunications(communicationsPacket)//.writeSignature().flush()
            sink
        }
    }

    /**
     * Returns true if the channel is open
     */
    fun isOpen(): Boolean = false //source.isOpen && sink.isOpen

}
