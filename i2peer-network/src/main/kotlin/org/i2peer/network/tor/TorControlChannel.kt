package org.i2peer.network.tor

import arrow.core.Either
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.actor
import okio.BufferedSink
import okio.BufferedSource
import org.i2peer.network.*
import java.util.*

/**
 * Service for communicating with a Tor control port
 */
class TorControlChannel(
    private val source: BufferedSource,
    private val sink: BufferedSink,
    private val networkContext: NetworkContext
) {

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

    /**
     * Reads from the Tor control endpoint
     */
    private val torControlReader = kotlin.concurrent.fixedRateTimer(
        name = "read-socket",
        initialDelay = 0, period = 300
    ) {
        while (!source.exhausted()) {
            val result = source.readTorControlResponse().fold({

            },
                {
                    GlobalScope.async {
                        torControlMessageTransformer.send(Either.right(it))
                    }
                })
        }
    }

    /**
     * Writes a TorControlMessage to the Tor control endpoint (sink)
     */
    @ObsoleteCoroutinesApi
    private val torControlWriter = GlobalScope.actor<TorControlMessage> {
        for (message in channel) {
            torControlMessageTransformer.send(Either.left(message))
            try {
                sink.write(message.encode())
                sink.flush()
            } catch (e: Exception) {
                e.printStackTrace()
                //TODO: Send error
            }
        }
    }

    /**
     *
     */
    @ObsoleteCoroutinesApi
    private val torControlMessageTransformer = GlobalScope.actor<Either<TorControlMessage, TorControlResponse>> {
        val messages = LinkedList<TorControlMessage>()
        for (message in channel) {
            when (message) {
                is Either.Left -> messages.add(message.a)
                is Either.Right -> when (message.b.code) {
                    250 -> callbackActor.send(TorControlTransaction(messages.pop(), message.b))
                    650 -> callbackActor.send(TorControlEvent(message.b))
                }
            }
        }
    }

    /**
     * Sends tor control events and transactions to any registered listeners (ActorRegistry)
     */
    @ObsoleteCoroutinesApi
    private val callbackActor = GlobalScope.actor<Any> {
        for (message in channel) {
            when (message) {
                is TorControlEvent -> {
                    networkContext.getActorChannels(TOR_CONTROL_EVENT).forEach {
                        it.send(TorControlEvent(message.response))
                    }
                }
                is TorControlTransaction -> {
                    networkContext.getActorChannels(TOR_CONTROL_TRANSACTION).forEach {
                        it.send(TorControlTransaction(message.request, message.response))
                    }
                }
                else -> {

                }
            }
        }
    }

    companion object {

        /**
         * Transforms a list of tor control replies into a map of key/value pairs
         */
        fun toMap(lines: List<ReplyLine>?): Map<String, String?> {
            if (lines == null) return mapOf()
            val map = HashMap<String, String?>(lines.size)
            for ((_, msg) in lines) {
                val kv = msg.split("[=]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                map[kv[0]] = if (kv.size == 2) kv[1] else null
            }
            return map
        }
    }
}