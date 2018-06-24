package org.i2peer.network

import arrow.core.Either
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class ProcessChannel(val source: BufferedSource, val sink: BufferedSink) {

    suspend fun send(communications: Communications) = writer.send(communications)

    private val writer = actor<Communications>(CommonPool) {
        for (communications in channel) {
            sink.write(communications.encode())
            sink.flush()
        }
    }

    companion object {

        suspend fun send(communications: Communications) = peerActor.send(communications)

        private val peerActor = actor<Communications>(CommonPool) {
            val channelMap: MutableMap<String, ProcessChannel> = ConcurrentHashMap()
            val torProxy: InetSocketAddress = InetSocketAddress.createUnresolved("127.0.0.1", 9100)
            for (communications in channel) {
                val result = getChannelFor(channelMap = channelMap, torProxy = torProxy,
                        receiverAddress = communications.process.port)
                when (result) {
                    is Either.Left -> {
                    }
                    is Either.Right -> {
                        result.b.send(communications)
                    }
                }
            }
        }

        @Synchronized
        private fun getChannelFor(receiverAddress: String, torProxy: InetSocketAddress, channelMap: MutableMap<String, ProcessChannel>)
                : Either<Exception, ProcessChannel> {
            var channel = channelMap[receiverAddress]
            if (channel == null) {
                try {
                    val result = createProcessChannel(onionAddress = receiverAddress, torProxy = torProxy)
                    when (result) {
                        is Either.Left -> return Either.left(result.a)
                        is Either.Right -> {
                            channel = result.b
                            //check if still open
                            channelMap[receiverAddress] = channel
                        }
                    }
                } catch (e: Exception) {
                    return Either.left(e)
                }
            }
            return Either.right(channel)
        }

        private fun createProcessChannel(socket: Socket = Socket(), onionAddress: String,
                                         torProxy: InetSocketAddress): Either<IOException, ProcessChannel> {
            try {
                socket.connect(torProxy, 5000)

                val dos = DataOutputStream(socket.getOutputStream())
                dos.writeByte(0x04)
                dos.writeByte(0x01)
                dos.writeShort(80)
                dos.writeInt(0x01)
                dos.writeByte(0x00)
                dos.write(onionAddress.toByteArray())
                dos.writeByte(0x00)

                val dis = DataInputStream(socket.getInputStream())
                dis.readByte()
                dis.readByte()
                dis.readShort()
                dis.readInt()

                return Either.right(ProcessChannel(source = Okio.buffer(Okio.source(dis)),
                        sink = Okio.buffer(Okio.sink(dos))))
            } catch (e: IOException) {
                return Either.left(e)
            }
        }
    }
}