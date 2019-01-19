package org.i2peer.network

import arrow.core.Try
import arrow.core.getOrElse
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio
import org.i2peer.auth.BasicAuthInfo
import org.i2peer.auth.NoAuthInfo
import org.i2peer.auth.TokenAuthInfo
import org.i2peer.auth.UnsupportedAuthInfo
import org.i2peer.network.tor.TorControlChannel
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Wraps [InputStream] and [OutputStream] in an Okio buffer. This provides us with convenient source/sink methods.
 */
object IO {
    fun source(input: InputStream): BufferedSource = Okio.buffer(Okio.source(input))

    fun sink(output: OutputStream): BufferedSink = Okio.buffer(Okio.sink(output))
}

fun BufferedSink.writeSignature(): BufferedSink {
    //TODO: implement signature - use key used with onion v2/v3
    // this.buffer().readByteArray()
    return this
}

/**
 * Writes a communications packet to the BufferedSink. This method flushes the sink before returning.
 */
fun BufferedSink.writeCommunications(communicationsPacket: CommunicationsPacket): BufferedSink {
    writeUtf(communicationsPacket.sourcePacketId)
    writeUtf(communicationsPacket.responsePacketId)
    writeUtf(communicationsPacket.sourcePort)
    writeProcess(communicationsPacket.targetProcess)
    writeAuthInfo(communicationsPacket.authInfo)
    writeLong(communicationsPacket.timestamp)
    writeMessage(communicationsPacket.message)

    flush()
    return this
}

/**
 * Writes a UTF string to the BufferedSink
 */
fun BufferedSink.writeUtf(value: String) {
    writeInt(value.length)
    writeUtf8(value)
}

/**
 * Writes [process] info to the BufferedSink
 */
fun BufferedSink.writeProcess(process: Process) {
    writeUtf(process.id)
    writeUtf(process.port)
    writeUtf(process.path)
}

/**
 * Writes a [message] to the BufferedSink
 */
fun BufferedSink.writeMessage(message: Message) {
    writeInt(message.type)
    writeLong(message.body.size.toLong())
    write(message.body)
}

/**
 * Writes [authInfo] to the BufferedSink. If [authInfo] is an instance of NoAuthInfo, then this method is a no op and
 * nothing will be written to the sink.
 */
fun BufferedSink.writeAuthInfo(authInfo: AuthInfo) {
    writeInt(authInfo.type)
    when (authInfo) {
        is NoAuthInfo -> return
        is BasicAuthInfo -> {
            writeUtf(authInfo.username)
            writeUtf(authInfo.password)
        }
        is TokenAuthInfo -> {
            writeUtf(authInfo.publicKey)
            writeUtf(authInfo.sessionToken)
        }
    }
}

/**
 * Connects to the socks proxy server and returns a [ProcessChannel] that has a [sink] for writing requests to the
 * proxy server and a [source] for reading responses from the proxy server. This method handles setting up the initial
 * handshake for the [sink] and the [source]
 */
fun Socket.openProcessChannel(
    socksHostname: String,
    socksPort: Int,
    socksConnectTimeout: Int,
    targetOnionAddress: String
): ProcessChannel {
    connectToPort(socksHostname, socksPort, socksConnectTimeout)
    val source = IO.source(getInputStream())
    val sink = IO.sink(getOutputStream())
    source.timeout().timeout(1000, TimeUnit.MILLISECONDS)
    sink.timeout().timeout(1000, TimeUnit.MILLISECONDS)

    sink.openSocksSink(targetOnionAddress)
    source.openSocksSource()

    return ProcessChannel(sink = sink, source = source, isConnected = true)
}

/**
 * Opens a connection to the specfied [hostName] and [port].
 */
fun Socket.connectToPort(hostname: String, port: Int, connectTimeout: Int): Socket {
    println("Connect to port: $hostname $port")
    connect(InetSocketAddress(hostname, port), connectTimeout)
    return this
}

fun Socket.openSocksSink(port: String): BufferedSink = IO.sink(getOutputStream()).openSocksSink(port)

fun Socket.openSocksSource(): BufferedSource = IO.source(getInputStream()).openSocksSource()

/**
 * Reads a [CommunicationsPacket] from the socket.
 */
fun Socket.readCommunications(): CommunicationsPacket = IO.source(getInputStream()).readCommunications()

fun BufferedSource.openSocksSource(): BufferedSource {
    println("Open socks source")
    val version = readByte()
    val status = readByte()
    if (status != 90.toByte()) throw IOException("Failed to connect to socks port: code = $status, version=$version")
    readShort()
    readInt()
    return this
}

fun BufferedSink.openSocksSink(onionAddress: String): BufferedSink {
    println("Open socks sink $onionAddress")
    if (!onionAddress.endsWith(".onion")) {
        println("Not valid onion address")
        return this
    }
    writeByte(0x04)//socks version
    writeByte(0x01)//stream connection
    writeShort(80)//virtual port - configured for hidden service
    writeInt(0x01)//invalid IP address
    writeByte(0x00)//user id -terminate
    write(onionAddress.toByteArray())//host to contact
    writeByte(0x00)//terminate host address
    flush()
    return this
}

/**
 * Reads [AuthInfo] from BufferedSource
 */
fun BufferedSource.readAuthInfo(): AuthInfo {
    when (readInt()) {
        0 -> return NoAuthInfo()
        1 -> return BasicAuthInfo(readUtf(), readUtf())
        2 -> return TokenAuthInfo(readUtf(), readUtf())
    }
    return UnsupportedAuthInfo()
}

fun BufferedSource.readProcess(): Process = Process(readUtf(), readUtf(), readUtf())

/**
 * Reads [CommunicationsPacket] from buffered source
 */
fun BufferedSource.readCommunications(): CommunicationsPacket = CommunicationsPacket(
    readUtf(), readUtf(),
    readUtf(), readProcess(),
    readAuthInfo(), readLong(), readMessage()
)//TODO: read and verify signature

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

fun File.setPermsRwx(): Try<Boolean> = Try {
    setReadable(true) && setExecutable(true) && setWritable(false) && setWritable(true, true)
}

fun File.createDir(): Boolean = exists() || mkdirs()

fun File.resolveParent(): File = if (parentFile.exists()) parentFile else this

fun File.setToReadOnlyPermissions(): Try<Boolean> {
    return Try {
        setReadable(false, false) && setWritable(false, false) &&
                setExecutable(false, false) &&
                setReadable(true, true) &&
                setWritable(true, true) &&
                setExecutable(true, true)
    }
}


fun InputStream.copyToFile(outputFile: File): Try<Long> {
    return Try {
        if (outputFile.exists() && !outputFile.delete())
            throw IOException("Unable to copy file: ${outputFile.absolutePath}")
        copyTo(FileOutputStream(outputFile))
    }
}
