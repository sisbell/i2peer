package org.i2peer.network.tor

import arrow.core.Either
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import okio.BufferedSink
import okio.BufferedSource
import org.i2peer.network.ActorRegistry
import org.i2peer.network.NetworkResponse
import org.i2peer.network.TorControlEvent
import org.i2peer.network.TorControlTransaction
import java.io.IOException
import java.util.*

class TorControlChannel(private val source: BufferedSource, private val sink: BufferedSink) {

    suspend fun addOnion(
        keyType: AddOnion.KeyType,
        keyBlob: String,
        ports: List<AddOnion.Port>,
        flags: List<AddOnion.OnionFlag>? = null,
        numStreams: Int? = 0,
        clientName: String? = null,
        clientBlob: String? = null
    ) = send(
        AddOnion(
            keyType = keyType, keyBlob = keyBlob, ports = ports,
            flags = flags, numStreams = numStreams, clientName = clientName, clientBlob = clientBlob
        )
    )

    suspend fun authenticate(value: ByteArray? = null) = send(Authenticate(value))

    suspend fun authchallenge() = send(AuthChallenge())

    suspend fun deleteOnion(serviceId: String) = send(DeleteOnion(serviceId))

    suspend fun dropGuards() = send(DropGuards())

    suspend fun extendCircuit(
        circuitID: String,
        serverSpec: List<String>? = null,
        purpose: ExtendCircuit.Purpose? = ExtendCircuit.Purpose.general
    ) = send(ExtendCircuit(circuitID, serverSpec, purpose))

    suspend fun loadConfiguration(configText: String) = send(LoadConfiguration(configText))

    suspend fun protocolInfo() = send(ProtocolInfo())

    suspend fun quit() = send(Quit())

    suspend fun resetConfiguration(params: Map<String, String>) = send(ResetConfiguration(params))

    suspend fun saveConfiguration(force: Boolean = false) = send(SaveConfiguration(force))

    suspend fun setConfiguration(params: Map<String, String>) = send(SetConfiguration(params))

    suspend fun setPassword(password: String) =
        setConfiguration(hashMapOf("HashedControlPassword" to password))

    suspend fun takeOwnership() = send(TakeOwnership())

    suspend fun send(message: TorControlMessage) = torControlWriter.send(message)

    private val socketReader = kotlin.concurrent.fixedRateTimer(
        name = "read-socket",
        initialDelay = 0, period = 300
    ) {
        while (!source.exhausted()) {
            val result = read(source)
            when (result) {
                is Either.Right -> launch {
                    torControlReader.send(Either.right(result.b))
                }
                is Either.Left -> {
                    println(result.a)
                }//???
            }
        }
    }

    private val torControlWriter = actor<TorControlMessage>(CommonPool) {
        for (message in channel) {
            torControlReader.send(Either.left(message))
            println("Write Message: " + String(message.encode()))
            try {
                sink.write(message.encode())
                sink.flush();
            } catch (e: Exception) {
                e.printStackTrace()
                //TODO: Send error
            }
        }
    }

    companion object {

        fun toMap(lines: List<ReplyLine>?): Map<String, String?> {
            if (lines == null) return mapOf()
            val map = HashMap<String, String?>(lines.size)
            for ((_, msg) in lines) {
                val kv = msg.split("[=]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                map[kv[0]] = if (kv.size == 2) kv[1] else null
            }
            return map
        }

        private val torControlReader = actor<Either<TorControlMessage, NetworkResponse>>(CommonPool) {
            val messages = LinkedList<TorControlMessage>()
            for (message in channel) {
                println("Reader message: ")
                when (message) {
                    is Either.Left -> messages.add(message.a)
                    is Either.Right -> when (message.b.code) {
                        250 -> callbackActor.send(TorControlTransaction(messages.pop(), message.b))
                        650 -> callbackActor.send(TorControlEvent(message.b))
                    }
                }
            }
        }

        private val callbackActor = actor<Any>(CommonPool) {
            for (message in channel) {

                when (message) {
                    is TorControlEvent -> {
                        println(message.response)
                        ActorRegistry.get(ActorRegistry.TOR_CONTROL_EVENT).forEach {
                            it.channel.send(TorControlEvent(message.response))
                        }
                    }
                    is TorControlTransaction -> {
                        println(String(message.request.encode()))
                        println(message.response)

                        ActorRegistry.get(ActorRegistry.TOR_CONTROL_TRANSACTION).forEach {
                            it.channel.send(TorControlTransaction(message.request, message.response))
                        }
                    }
                    else -> {
                        println("callback else")
                    }
                }
            }
        }

        private fun readReply(input: BufferedSource): Either<IOException, LinkedList<ReplyLine>> {
            val reply = LinkedList<ReplyLine>()
            var c: Char
            do {
                try {
                    var line = input.readUtf8Line()
                    println(line)
                    if (line == null && reply.isEmpty()) return Either.right(reply)

                    val status = line!!.substring(0, 3)
                    val msg = line.substring(4)
                    var rest: String? = null

                    c = line.get(3)
                    if (c == '+') {
                        val data = StringBuilder()
                        while (true) {
                            line = input.readUtf8Line()
                            if (line === ".")
                                break
                            if (line!!.startsWith("."))
                                line = line.substring(1)
                            data.append("$line\n")
                        }
                        rest = data.toString()
                    }
                    reply.add(ReplyLine(status.toInt(), msg, rest))
                } catch (e: IOException) {
                    return Either.left(e)
                }
            } while (c != ' ')

            return Either.right(reply)
        }

        private fun read(source: BufferedSource): Either<IOException, NetworkResponse> {
            val lines = readReply(source)
            return when (lines) {
                is Either.Right -> {
                    val last = lines.b.peekLast()
                    if ("OK" === last.msg) lines.b.removeLast()
                    Either.right(NetworkResponse(last.status, last.msg, toMap(lines.b)))
                }
                is Either.Left -> Either.left(lines.a)
            }
        }
    }
}