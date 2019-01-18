package org.i2peer.network

import com.google.common.collect.ArrayListMultimap
import kotlinx.coroutines.channels.SendChannel
import org.i2peer.network.tor.TorConfig
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * Tor control event (code)
 */
const val TOR_CONTROL_EVENT = "system.TOR_CONTROL_EVENT"
const val TOR_CONTROL_TRANSACTION = "system.TOR_CONTROL_TRANSACTION"

class NetworkContext(val torConfig: TorConfig) {

    /**
     * Caches all the process channels, which hold outgoing connections
     */
    val channelMap: MutableMap<String, ProcessChannel> = ConcurrentHashMap()

    val localOnionAddress = ArrayList<String>()

    private val actors = ArrayListMultimap.create<String, SendChannel<Any>>()

    @Synchronized
    fun addActorChannel(id: String, actor: SendChannel<Any>) = actors.put(id, actor)

    @Synchronized
    fun removeActorChannel(id: String, actor: SendChannel<Any>) = actors.remove(id, actor)

    @Synchronized
    fun getActorChannels(id: String): List<SendChannel<Any>> = Collections.unmodifiableList(actors.get(id))

    @Synchronized
    fun emptyActorChannels() = actors.clear()

}