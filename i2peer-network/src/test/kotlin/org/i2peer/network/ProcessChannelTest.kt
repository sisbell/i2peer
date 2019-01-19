package org.i2peer.network

import org.i2peer.auth.NoAuthInfo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class ProcessChannelTest {

    @Test
    suspend fun send() {
        val targetProcess = Process("myId", "ikvh2uz76knum6dw", "path")
        val message = Message(3, "Hello Onion".toByteArray())
        val communicationsPacket =
            CommunicationsPacket("ABC", "123", "8080", targetProcess, NoAuthInfo(), 1000, message)

        val boas = ByteArrayOutputStream()
        val sink = IO.sink(boas)
        val source = IO.source(ByteArrayInputStream(boas.toByteArray()))
        val processChannel = ProcessChannel(source, sink, false)

        processChannel.sendCommunications(communicationsPacket).await()
        val targetCommunicationPacket = processChannel.source.readCommunications()

        assertEquals(communicationsPacket, targetCommunicationPacket)
    }
}