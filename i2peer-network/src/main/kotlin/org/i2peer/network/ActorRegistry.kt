package org.i2peer.network

import com.google.common.collect.ArrayListMultimap
import kotlinx.coroutines.experimental.channels.ActorScope
import kotlinx.coroutines.experimental.channels.Send
import kotlinx.coroutines.experimental.channels.SendChannel
import java.util.*

/**
 * Registry service for kotlin actors
 */
class ActorRegistry {

    companion object {
        private val actors = ArrayListMultimap.create<String, SendChannel<Any>>()

        @Synchronized
        fun add(id: String, actor: SendChannel<Any>) = actors.put(id, actor)

        @Synchronized
        fun remove(id: String, actor: SendChannel<Any>) = actors.remove(id, actor)

        @Synchronized
        fun get(id: String): List<SendChannel<Any>> = Collections.unmodifiableList(actors.get(id))

        /**
         * Tor control event (code)
         */
        const val TOR_CONTROL_EVENT = "system.TOR_CONTROL_EVENT"
        const val TOR_CONTROL_TRANSACTION = "system.TOR_CONTROL_TRANSACTION"
    }
}