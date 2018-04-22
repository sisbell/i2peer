package org.i2peer.network

import com.google.common.collect.ArrayListMultimap
import kotlinx.coroutines.experimental.channels.ActorScope
import java.util.*

class ActorRegistry {

    companion object {
        private val actors = ArrayListMultimap.create<String, ActorScope<Any>>()

        @Synchronized
        fun add(id: String, actor: ActorScope<Any>) = actors.put(id, actor)

        @Synchronized
        fun remove(id: String, actor: ActorScope<Any>) = actors.remove(id, actor)

        @Synchronized
        fun get(id: String): List<ActorScope<Any>> = Collections.unmodifiableList(actors.get(id))

        const val TOR_CONTROL_EVENT = "system.TOR_CONTROL_EVENT"
        const val TOR_CONTROL_TRANSACTION = "system.TOR_CONTROL_TRANSACTION"
    }
}