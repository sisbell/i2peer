package org.i2peer.network

import arrow.core.Try
import kotlinx.coroutines.experimental.channels.Channel
import okio.ByteString
import org.i2peer.network.tor.TorConfig
import org.i2peer.network.tor.TorControlMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object Files {
    fun getResourceStream(fileName: String): Try<InputStream> = Try { javaClass.getResourceAsStream("/$fileName") }

    fun setToReadOnlyPermissions(file: File): Try<Boolean> {
        return Try {
            file.setReadable(false, false) &&
                    file.setWritable(false, false) &&
                    file.setExecutable(false, false) &&
                    file.setReadable(true, true) &&
                    file.setWritable(true, true) &&
                    file.setExecutable(true, true)
        }
    }

    fun setPerms(file: File): Try<Boolean> {
        return Try {
            file.setReadable(true) &&
                    file.setExecutable(true) &&
                    file.setWritable(false) &&
                    file.setWritable(true, true)
        }
    }

    fun copyFile(input: InputStream, outputFile: File): Try<Long> {
        return Try {
            if (outputFile.exists() && !outputFile.delete())
                throw IOException("Unable to copy file: ${outputFile.absolutePath}")
            input.copyTo(FileOutputStream(outputFile))
        }
    }

    fun copyResource(resourceName: String, outputFile: File): Try<Long> {
        return getResourceStream(resourceName).fold({ throw IOException("Resource not found: $resourceName") }, {
            copyFile(it, outputFile)
        })
    }

    fun createDir(dir: File): Boolean = dir.exists() || dir.mkdirs()

    fun resolveParent(file: File): File = if (file.parentFile.exists()) file.parentFile else file

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
 * A Process is an abstraction that can perform some computation
 *
 * @property id a unique identity for this targetProcess
 * @property port the endpoint of the targetProcess. In our case, this is the onion address. Other application transports
 *  could use an IP Address
 * @property path defines a destination (or name) of the service similar to a URL path
 */
data class Process(val id: String, val port: String, val path: String)

data class Message(val type: Int, val body: ByteArray)

abstract class EventTask {
    abstract var name: String
}

/**
 *
 */
data class ChannelTask(override var name: String, val channel: Channel<EventTask>) : EventTask()

/**
 * Wraps communication so we can define its type
 *
 * @property name type of task: SEND or DELIVER
 */
data class CommunicationTask(override var name: String, val communications: Communications) : EventTask()

/**
 *
 * @property sourcePort the onion address of the process that is sending the communications
 * @property targetProcess the process to send the communications to
 * @property message the message to send
 */
data class Communications(val sourcePort: String, val targetProcess: Process, val message: Message)

lateinit var config: TorConfig

fun <T> loggerFor(clazz: Class<T>) = LoggerFactory.getLogger(clazz)


