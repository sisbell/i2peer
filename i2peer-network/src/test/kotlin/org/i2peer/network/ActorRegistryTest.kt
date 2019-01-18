package org.i2peer.network

import kotlinx.coroutines.channels.Channel
import org.i2peer.network.tor.TorConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class networkContextTest {

    lateinit var networkContext: NetworkContext

    @BeforeEach
    fun setup() {
        val torConfig = TorConfig(socksPort = 1000, configDir = File("mydir/" + Math.random()))
        networkContext = NetworkContext(torConfig)
    }

    @Test
    fun getUnknownActorReturnsEmptyCollection() {
        val g = networkContext.getActorChannels("dummy")
        assert(g.isEmpty())
    }

    @Test
    fun addActorToRegistry() {
        networkContext.addActorChannel("main", Channel())
        val g = networkContext.getActorChannels("main")
        assert(!g.isEmpty())
    }

    @Test
    fun addTwoActorsToRegistry() {
        networkContext.addActorChannel("main", Channel())
        networkContext.addActorChannel("main", Channel())
        val g = networkContext.getActorChannels("main")
        assertEquals(2, g.size)
    }

    @Test
    fun removeActorFromRegistryWithTwoItems() {
        val channel = Channel<Any>()
        networkContext.addActorChannel("main", channel)
        networkContext.addActorChannel("main", Channel())
        networkContext.removeActorChannel("main", channel)

        val g = networkContext.getActorChannels("main")
        assertEquals(1, g.size)
    }

    @Test
    fun removeActorFromRegistry() {
        val channel = Channel<Any>()
        networkContext.addActorChannel("main", channel)
        networkContext.removeActorChannel("main", channel)
        val g = networkContext.getActorChannels("main")
        assert(g.isEmpty())
    }

    @Test
    fun removeUnknownActorNoException() {
        val channel = Channel<Any>()
        networkContext.removeActorChannel("any", channel)
    }
}