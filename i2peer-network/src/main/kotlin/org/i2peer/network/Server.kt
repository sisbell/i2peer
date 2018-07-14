package org.i2peer.network

import kotlinx.coroutines.experimental.runBlocking
import java.net.ServerSocket
import kotlin.concurrent.thread

fun ServerSocket.deliverCommunications() {

    use {
        while (!isClosed) {
            println("Waiting to accept")
            val socket = accept()
            println("Client connected: ${socket.inetAddress.hostAddress}")

            thread(start = true) {
                    runBlocking {
                        try {
                            println("Deliver communicationsPacket")
                            fairLossPointToPoint().deliver(socket.readCommunications())
                            //TODO: read batched communicationsPacket
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            socket.close()
                            println("Close client: " + isClosed)
                        }
                    }
                    println("Returning thread")
                    return@thread
            }
            println("Looping...")
        }
    }
}