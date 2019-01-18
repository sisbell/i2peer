package org.i2peer.network.tor

import org.i2peer.network.CodeGenerator
import org.i2peer.network.Encoder.hex

sealed class TorControlMessage {

    abstract fun encode(): ByteArray

    protected fun noArgs(command: String) = "$command\r\n".toByteArray()

    protected fun joinArgs(command: String, keywords: List<String?>): ByteArray =
        keywords.filter { !it.isNullOrBlank() }.joinToString(
            separator = " ",
            prefix = "$command ",
            postfix = "\r\n"
        ).toByteArray()
}

class AddOnion(
    private val keyType: KeyType,
    private val keyBlob: String,
    private val ports: List<Port>,
    private val flags: List<OnionFlag>? = null,
    private val numStreams: Int? = 0,
    private val clientName: String? = null,
    private val clientBlob: String? = null
) : TorControlMessage() {

    override fun encode(): ByteArray {
        var result = "ADD_ONION $key"
        if (flags != null) result += " ${flag()}"
        result += " $maxStreams $port"
        if (clientName != null) result += " $client"
        return ("$result\r\n").toByteArray()
    }

    private val key = "${keyType.name}:$keyBlob"

    private fun flag() = "Flags=${flags!!.joinToString()}"

    private val maxStreams = "MaxStreams=$numStreams"

    private val port = ports.joinToString(separator = " ")

    private val client = clientName + if (clientBlob != null) ":$clientBlob" else ""

    enum class KeyType(value: String) {
        NEW("NEW"), RSA1024("RSA1024"), ED25519V3("ED25519-V3")
    }

    class Port(private val virtualPort: Int, private val target: String? = null) {
        override fun toString() = "Port=$virtualPort" + if (target != null) ",$target" else ""
    }

    enum class OnionFlag {
        DiscardPk, Detach, BasicAuth, NonAnonymous, MaxStreamsCloseCircuit
    }
}

class Authenticate(private val value: ByteArray? = null) : TorControlMessage() {
    override fun encode(): ByteArray = if (value == null) noArgs(command) else joinArgs(
        command,
        listOf(hex(value))
    )

    private val command = "AUTHENTICATE"
}

class AuthChallenge : TorControlMessage() {
    override fun encode() = joinArgs("AUTHCHALLENGE", listOf("SAFECOOKIE", nonce()))

    private fun nonce() = hex("12") + "/${CodeGenerator.generateCode(32)}"

}

class DeleteOnion(private val serviceId: String) : TorControlMessage() {
    override fun encode() = joinArgs("DEL_ONION", listOf(serviceId))
}

class DropGuards : TorControlMessage() {
    override fun encode() = noArgs("DROPGUARDS")
}

class ExtendCircuit(
    private val circuitID: String, private val serverSpec: List<String>? = null,
    private val purpose: Purpose? = Purpose.general
) : TorControlMessage() {
    override fun encode(): ByteArray =
        joinArgs(
            "EXTENDCIRCUIT", listOf(
                circuitID, serverSpec!!.joinToString { "," },
                if (purpose != null) "purpose=$purpose" else null
            )
        )

    enum class Purpose {
        general, controller
    }
}

class LoadConfiguration(private val configText: String) : TorControlMessage() {
    override fun encode() = "+LOADCONF\r\n$configText\r\n.\r\n".toByteArray()
}

class ProtocolInfo : TorControlMessage() {
    override fun encode() = noArgs("PROTOCOLINFO")
}

class ResetConfiguration(private val params: Map<String, String>) : TorControlMessage() {
    override fun encode() = joinArgs("RESETCONF", params.map({ "${it.key}=${it.value}" }))
}

class SaveConfiguration(private val force: Boolean) : TorControlMessage() {
    override fun encode() = if (force) joinArgs("SAVECONF", listOf("FORCE")) else noArgs("SAVECONF")
}

class SetEvents(private val events: List<Events>) : TorControlMessage() {
    override fun encode() = joinArgs("SETEVENTS", events.map { it.name })

    enum class Events {
        CIRC, STREAM, ORCONN, BW, DEBUG, INFO, NOTICE, WARN, ERR, NEWDESC, ADDRMAP, AUTHDIR_NEWDESCS
    }
}

class Signal(private val type: SignalType) : TorControlMessage() {

    enum class SignalType {
        RELOAD, SHUTDOWN, DUMP, DEBUG, HALT, HUP, INT, USR1, USR2, TERM, NEWNYM, CLEARDNSCACHE, HEARTBEAT
    }

    override fun encode() = joinArgs("SIGNAL", listOf(type.name))
}

class SetConfiguration(private val params: Map<String, String>) : TorControlMessage() {
    override fun encode() = joinArgs("SETCONF", params.map({ "${it.key}=${it.value}" }))
}

class TakeOwnership : TorControlMessage() {
    override fun encode() = noArgs("TAKEOWNERSHIP")
}

class Quit : TorControlMessage() {
    override fun encode() = noArgs("QUIT")
}

/*
class AuthChallenge(private val clientNonce: ClientNonce) : TorControlMessage() {
    fun encode() = "AUTHCHALLENGE SAFECOOKIE ${clientNonce.asText()}\r\n".getBytes()
}
*/

class GetConfiguration(private val keywords: List<String>) : TorControlMessage() {
    override fun encode() = joinArgs("GETCONF", keywords)
}

class GetInfo(private val info: List<String>) : TorControlMessage() {

    override fun encode() = joinArgs("GETINFO", info)

    companion object {

        const val ADDRESS_MAPPINGS_ALL = "address-mappings/all"

        const val ONIONS_CURRENT = "onions/current"

        const val ONIONS_DETACHED = "onions/detached"

        const val NETWORK_LIVENESS = "org.i2peer.network-liveness"

        const val TRAFFIC_READ = "traffic/read"

        const val TRAFFIC_WRITTEN = "traffic/written"
    }

}



