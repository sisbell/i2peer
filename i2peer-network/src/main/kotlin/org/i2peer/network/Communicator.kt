package org.i2peer.network

class Communicator {

    suspend fun send(message: Message) {
      //  val process = Process("myId", onionField.characters.toString())
     //   val message = Message(messageField.characters.toString().toByteArray())
     //   val communications = Communications(process, message, 4)
     //   perfectPointToPoint().send(CommunicationTask("Send", communications))
    }

    suspend fun broadcast(message: Message) {

    }
}