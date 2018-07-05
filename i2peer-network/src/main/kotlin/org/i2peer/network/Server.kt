package org.i2peer.network

import kotlinx.coroutines.experimental.async
import java.net.ServerSocket

fun ServerSocket.deliverCommunications() {
    async {
        while (!isClosed) {
            val client = accept()
            println("Client connected: ${client.inetAddress.hostAddress}")

            async {
                try {
                    fairLossPointToPoint().deliver(client.readCommunications())
                    //TODO: read batched communications
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                client.close()
            }
        }
    }
}