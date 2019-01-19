package org.i2peer.network

import arrow.core.Try
import kotlinx.coroutines.channels.Channel
import okio.ByteString
import org.i2peer.network.tor.TorControlMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream

object Files {
    fun getResourceStream(fileName: String): Try<InputStream> = Try { javaClass.getResourceAsStream("/$fileName") }

    fun copyResource(resourceName: String, outputFile: File): Try<Long> {
        return getResourceStream(resourceName).fold({ throw Exception("Resource not found: $resourceName") }, {
            it.copyToFile(outputFile)
        })
    }
}

object Encoder {
    fun quote(value: String) = "\"$value\""

    fun hex(value: String): String = ByteString.encodeUtf8(value).hex()

    fun hex(value: ByteArray): String = ByteString.of(value, 0, value.size).hex()
}

enum class OsType {
    WINDOWS, LINUX_64, MAC, ANDROID, UNSUPPORTED
}

fun osType(
    vmName: String = System.getProperty("java.vm.name"),
    osName: String = System.getProperty("os.name")
): OsType {
    if (vmName.contains("Dalvik")) return OsType.ANDROID
    return when {
        osName.contains("Windows") -> OsType.WINDOWS
        osName.contains("Mac") -> OsType.MAC
        osName.contains("Linux") -> OsType.LINUX_64
        else -> OsType.UNSUPPORTED
    }
}

/**
 * Reply line from Tor control protocol
 */
data class ReplyLine(val status: Int, val msg: String, val rest: String?)

data class TorControlResponse(val code: Int, val message: String, val body: Any? = Unit)

data class TorControlEvent(val response: TorControlResponse)

data class TorControlTransaction(val request: TorControlMessage, val response: TorControlResponse)

/**
 * A Process is an abstraction that can perform some computation. The specified publicKey and sessionToken
 * are used by the process to decide if this process will accept the message.
 *
 * @property id a unique identity for this targetProcess
 * @property port the endpoint of the targetProcess. In our case, this is the onion address. Other application transports
 *  could use an IP Address
 * @property path defines a destination (or name) of the service similar to a URL path
 */
data class Process(val id: String, val port: String, val path: String)

/**
 * Message data class. [type] specifies the type of message, which is arbitrary.
 */
data class Message(val type: Int, val body: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (type != other.type) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + body.contentHashCode()
        return result
    }
}

abstract class EventTask {
    abstract var name: String
}

interface CommunicationTaskMatcher {
    /**
     * Returns true if [task] matches rule, otherwise returns false
     */
    fun match(task: CommunicationTask): Boolean
}

interface CommunicationPacketMatcher {
    /**
     * Returns true if [communicationsPacket] matches rule, otherwise returns false
     */
    fun match(communicationsPacket: CommunicationsPacket) : Boolean
}

/**
 * Matcher that whitelists any [CommunicationTask]
 */
class AnyCommunicationTaskMatcher : CommunicationTaskMatcher {
    override fun match(task: CommunicationTask): Boolean {
        return true
    }
}

/**
 * A delivery channel that is used to pass messages up the network stack. The [matchers] are used to determine which
 * [CommunicationTask]s are allowed to continue up the stack.
 */
class DeliveryChannel(val channel: Channel<EventTask>, val matchers: List<CommunicationTaskMatcher>) {
    fun match(task: CommunicationTask): Boolean = matchers.all { it.match(task) }
}

/**
 * The ChannelTask ties together a task [name] with a deliveryChannel. For instance, ChannelTask("REGISTER", deliveryChannel)
 * would create a data class that says the deliveryChannel should be registered.
 */
data class ChannelTask(override var name: String, val deliveryChannel: DeliveryChannel) : EventTask()

/**
 * Wraps [communicationsPacket] so we can define its type
 *
 * @property name type of task: SEND or DELIVER
 */
data class CommunicationTask(override var name: String, val communicationsPacket: CommunicationsPacket) : EventTask()

/**
 * A packet that contains source and target process info with an embedded [message]. The [sourcePort] is the
 * onion address of the sender to maintain anonymity.
 *
 * @property sourcePort the onion address of the process that is sending the communicationsPacket
 * @property targetProcess the process to send the communicationsPacket to
 * @property message the message to send
 * @property authInfo authentication info used to decide whether to process this communication. If no auth info is required use NoAuthInfo instance.
 */
data class CommunicationsPacket(
    val sourcePacketId: String = CodeGenerator.generateCode(),
    val responsePacketId: String,
    val sourcePort: String,
    val targetProcess: Process,
    val authInfo: AuthInfo,
    val timestamp: Long,
    val message: Message
)

abstract class AuthInfo(val type: Int)

fun <T> loggerFor(clazz: Class<T>) = LoggerFactory.getLogger(clazz)


