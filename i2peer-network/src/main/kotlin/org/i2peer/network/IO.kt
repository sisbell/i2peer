package org.i2peer.network

import arrow.core.Try
import arrow.core.getOrElse
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio
import org.i2peer.network.tor.TorControlChannel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

object IO {
    fun source(input: InputStream): BufferedSource = Okio.buffer(Okio.source(input))

    fun sink(output: OutputStream): BufferedSink = Okio.buffer(Okio.sink(output))
}

fun BufferedSink.writeCommunications(communications: Communications): BufferedSink {
    writeUtf(communications.sourcePort)
    writeProcess(communications.targetProcess)
    writeMessage(communications.message)
    flush()
    return this
}

fun BufferedSink.writeUtf(value: String) {
    writeInt(value.length)
    writeUtf8(value)
}

fun BufferedSink.writeProcess(process: Process) {
    writeUtf(process.id)
    writeUtf(process.port)
    writeUtf(process.path)
}

fun BufferedSink.writeMessage(message: Message) {
    writeInt(message.type)
    writeLong(message.body.size.toLong())
    write(message.body)
}

fun Socket.openProcessChannel(socksHostname: String, socksPort: Int, socksConnectTimeout: Int, targetOnionAddress: String): ProcessChannel {
    connectToSocksPort(socksHostname, socksPort, socksConnectTimeout)
    val sink = openTorSink(targetOnionAddress)
    val source = openTorSource()
    return ProcessChannel(sink = sink, source = source)
}

fun Socket.connectToSocksPort(hostname: String, socksPort: Int, connectTimeout: Int): Socket {
    println("Connecting..." + hostname + "," + socksPort)
    connect(InetSocketAddress(hostname, socksPort), connectTimeout)
    println("Connected!!!")
    return this
}

fun Socket.openTorSink(port: String): BufferedSink = IO.sink(getOutputStream()).openTorSink(port)

fun Socket.openTorSource(): BufferedSource = IO.source(getInputStream()).openTorSource()

fun Socket.readCommunications() : Communications = IO.source(getInputStream()).readCommunications()

fun BufferedSource.openTorSource(): BufferedSource {
    val version = readByte()
    val status = readByte()
    if (status != 90.toByte()) throw IOException("Failed to connect to socks port: code = " + status)
    readShort()
    readInt()
    return this
}

fun BufferedSink.openTorSink(port: String): BufferedSink {
    writeByte(0x04)
    writeByte(0x01)
    writeShort(80)//port
    writeInt(0x01)
    writeByte(0x00)
    write(port.toByteArray())
    writeByte(0x00)
    flush()
    return this
}

fun BufferedSource.readProcess(): Process = Process(readUtf(), readUtf(), readUtf())

fun BufferedSource.readCommunications(): Communications = Communications(readUtf(), readProcess(), readMessage())

fun BufferedSource.readMessage(): Message = Message(readInt(), readByteArray(readLong()))

fun BufferedSource.readUtf(): String = readUtf8(readInt().toLong())

fun BufferedSource.readTorControlReply(): Try<LinkedList<ReplyLine>> {
    return Try {
        val reply = LinkedList<ReplyLine>()
        var c: Char
        do {
            var line = readUtf8Line()
            println(line)
            if (line == null && reply.isEmpty()) break

            val status = line!!.substring(0, 3)
            val msg = line.substring(4)
            var rest: String? = null

            c = line.get(3)
            if (c == '+') {
                val data = StringBuilder()
                while (true) {
                    line = readUtf8Line()
                    if (line === ".")
                        break
                    if (line!!.startsWith("."))
                        line = line.substring(1)
                    data.append("$line\n")
                }
                rest = data.toString()
            }
            reply.add(ReplyLine(status.toInt(), msg, rest))
        } while (c != ' ')
        reply
    }
}

fun BufferedSource.readTorControlResponse(): Try<TorControlResponse> {
    return Try {
        val lines = readTorControlReply().getOrElse { null }
        val last = lines!!.peekLast()
        if ("OK" === last.msg) lines.removeLast()
        TorControlResponse(last.status, last.msg, TorControlChannel.toMap(lines))
    }
}