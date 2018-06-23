package org.i2peer.network

import arrow.core.Try
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.Okio
import org.i2peer.network.tor.TorControlMessage
import java.io.*

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

object IO {
    fun source(input: InputStream): BufferedSource = Okio.buffer(Okio.source(input))

    fun sink(output: OutputStream): BufferedSink = Okio.buffer(Okio.sink(output))
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

data class NetworkResponse(val code: Int, val message: String, val body: Any? = Unit)

data class Requirements(val version: Int, val services: Set<String>)

data class TorControlEvent(val response: NetworkResponse)

data class TorControlTransaction(val request: TorControlMessage, val response: NetworkResponse)

abstract class NetworkMessage(
    val senderAddress: String? = null,
    val recipientAddress: String? = null,
    val requirements: Requirements? = null
) {
    abstract fun encode(): ByteArray
}

data class Process(val id: String, val port: String)

data class Message(val body: ByteArray)

abstract class EventTask {
    abstract var name: String
}

data class ChannelTask(override var name: String, val channel: Channel<EventTask>) : EventTask()

data class CommunicationTask(override var name: String, val communications: Communications) : EventTask()

data class Communications(val process: Process, val message: Message, val type: Int)

