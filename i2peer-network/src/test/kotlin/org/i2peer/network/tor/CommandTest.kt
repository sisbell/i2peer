package org.i2peer.network.tor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandTest {

    @Test
    fun signal() {
        val result = String(Signal(Signal.SignalType.DEBUG).encode())
        assertEquals("SIGNAL DEBUG\r\n", result)
    }

    @Test
    fun getConfig() {
        val result = String(GetConfiguration(listOf("what", "ever")).encode())
        assertEquals("GETCONF what ever\r\n", result)
    }

    @Test
    fun getInfo() {
        val result = String(
            GetInfo(
                listOf(
                    GetInfo.ADDRESS_MAPPINGS_ALL,
                    GetInfo.NETWORK_LIVENESS
                )
            ).encode()
        )
        assertEquals("GETINFO ${GetInfo.ADDRESS_MAPPINGS_ALL} ${GetInfo.NETWORK_LIVENESS}\r\n", result)
    }

    @Test
    fun addOnion() {
        val result = String(
            AddOnion(
                keyType = AddOnion.KeyType.NEW,
                flags = listOf(
                    AddOnion.OnionFlag.BasicAuth,
                    AddOnion.OnionFlag.MaxStreamsCloseCircuit
                ),
                keyBlob = "fdadsfhj",
                numStreams = 5,
                clientName = "myclient",
                clientBlob = "myclientblob",
                ports = listOf(AddOnion.Port(5012), AddOnion.Port(5000, "foo"))
            ).encode()
        )
        println(result)
    }

    @Test
    fun addOnion2() {
        val result = String(
            AddOnion(
                keyType = AddOnion.KeyType.NEW,
                keyBlob = "BEST",
                ports = listOf(AddOnion.Port(5012))
            ).encode()
        )
        println(result)
    }

    @Test
    fun regex() {
        val result =
            boostrapRegex.find("StartProcessMessage(message=Apr 22 14:30:07.000 [notice] Bootstrapped 80%: Connecting to the Tor network)")
        assertEquals("80", result!!.groups[1]!!.value)
    }
}