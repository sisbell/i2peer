package org.i2peer.network

import org.i2peer.auth.NoAuthInfo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class IOTest {
    @Test
    fun readWriteCommunications() {
        val targetProcess = Process("myId", "ikvh2uz76knum6dw", "path")
        val message = Message(3, "Hello Onion".toByteArray())
        val communicationsPacket = CommunicationsPacket("ABC", "123", "8080", targetProcess, NoAuthInfo(), 1000, message)

        val boas = ByteArrayOutputStream()
        val sink = IO.sink(boas)

        sink.writeCommunications(communicationsPacket)

        val source = IO.source(ByteArrayInputStream(boas.toByteArray()))
        val sourceCommunicationPacket = source.readCommunications()
        assertEquals(communicationsPacket, sourceCommunicationPacket)
    }

    @Test
    fun readWriteUtf() {
        val boas = ByteArrayOutputStream()
        val sink = IO.sink(boas)
        sink.writeUtf8("foo")
        sink.flush()
        val source = IO.source(ByteArrayInputStream(boas.toByteArray()))
        val result = source.readUtf8()
        assertEquals("foo", result)
    }

    @Test
    fun readWriteProcess() {
        val targetProcess = Process("myId", "ikvh2uz76knum6dw", "path")
        val boas = ByteArrayOutputStream()
        val sink = IO.sink(boas)
        sink.writeProcess(targetProcess)
        sink.flush()
        val source = IO.source(ByteArrayInputStream(boas.toByteArray()))
        val result = source.readProcess()
        assertEquals(targetProcess, result)
    }
}

