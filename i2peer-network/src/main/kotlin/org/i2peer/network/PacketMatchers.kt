package org.i2peer.network

class PathPacketMatcher(val path: String) : CommunicationPacketMatcher {
    override fun match(communicationsPacket: CommunicationsPacket): Boolean {
        return communicationsPacket.targetProcess.path == path
    }
}