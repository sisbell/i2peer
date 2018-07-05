package org.i2peer.network.tor

import okio.Okio
import org.i2peer.network.Communications
import org.i2peer.network.Message
import org.i2peer.network.Process
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class CommunicationsTest {

    @Test
    fun encodeDecode() {
       // val process = Process("myId", "ikvh2uz76knum6dw")
       // val message = Message("Hello Onion".toByteArray())
       // val communications = Communications(process, message, 4)
      //  val encoded = communications.encode()
    //    encoded.flush()

        //val input = ByteArrayInputStream(encoded.buffer().readByteArray())
        //val result = Communications.read(Okio.buffer(Okio.source(input)))
       // println(result.targetProcess.port)

    }
}