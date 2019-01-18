package org.i2peer.network

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.net.ServerSocket

/**
 * Provides communications to the "outside world". This class creates a socket listener which reads data and then
 * delivers a [CommunicationsPacket] to a [FairLossPointToPoint] link, which in turn delivers the message up the
 * network stack.
 */
@ObsoleteCoroutinesApi
fun ServerSocket.deliverCommunications(entryChannel: SendChannel<EventTask>) {

    use {
        while (!isClosed) {
            println("Waiting to accept")
            val socket = accept()
            println("Client connected: ${socket.inetAddress.hostAddress}")
            GlobalScope.launch {
                try {
                    println("Deliver communicationsPacket")
                    entryChannel.deliver(socket.readCommunications())
                    //TODO: read batched communicationsPacket
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    socket.close()
                    println("Close client:  $isClosed")
                }
            }
            println("Looping...")
        }
    }
}