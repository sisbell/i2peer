package org.i2peer.network.tor

import org.i2peer.network.Files.createDir
import org.i2peer.network.Files.setPerms
import org.i2peer.network.tor.Installer.copyFiles
import org.i2peer.network.tor.Installer.unzipArchive
import org.i2peer.network.tor.TorConfig.Companion.writeConfig
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import java.io.ByteArrayOutputStream
import java.lang.System.currentTimeMillis

data class TorStartData(val torConfig: TorConfig, val event: SendChannel<Any>)
data class EventMessage(val text: String)
data class EventError(val message: String, val e: Throwable? = null)
data class StartProcessMessage(val message: String)
data class StartOk(val message: String, val torProcess: Process)
data class Bootstrap(val percentage: Double)
suspend fun startTor(torConfig: TorConfig, event: SendChannel<Any>) =
    torConfigWriter().send(TorStartData(torConfig, event))

fun torConfigWriter() = actor<TorStartData>(CommonPool) {
    val data = channel.receive()
    data.event.send(EventMessage("TorConfigWriter"))

    if (createDir(data.torConfig.dataDir)) {
        writeConfig(data.torConfig).fold({
            data.event.send(EventError("Failed to write config files", it))
        }, {
            torInstallNeededChecker().send(data)
        })
    } else {
        data.event.send(EventError("Failed to create dataDir ${data.torConfig.dataDir.absolutePath}"))
    }

    channel.cancel()
}

fun torInstallNeededChecker() = actor<TorStartData>(CommonPool) {
    val data = channel.receive()
    data.event.send(EventMessage("ToInstallCheck"))

    val config = data.torConfig
    if (doResourceFilesExist(
            geoIpFile = config.geoIpFile,
            geoIpv6File = config.geoIpv6File,
            torrcFile = config.torrcFile
        )
        && config.torExecutableFile.exists()
    ) {
        torStarter().send(data)
    } else {
        torProgramUnarchiver().send(data)
    }

    channel.cancel()
}

fun torProgramUnarchiver() = actor<TorStartData>(CommonPool) {
    val data = channel.receive()
    data.event.send(EventMessage("TorUnarchiver"))

    val config = data.torConfig
    unzipArchive(config.torExecutableFile).fold(
        { data.event.send(EventError("Failed to unzip tor archive", it)) },
        {
            setPerms(config.torExecutableFile)
                .fold({ data.event.send(EventError("Failed to set permissions on tor", it)) },
                    { torConfigFilesCopier().send(data) })
        })
    channel.cancel()
}

fun torConfigFilesCopier() = actor<TorStartData>(CommonPool) {
    val data = channel.receive()
    data.event.send(EventMessage("Copier"))

    val config = data.torConfig
    copyFiles(geoIpFile = config.geoIpFile, geoIpv6File = config.geoIpv6File)
        .fold({ data.event.send(EventError("Failed to copy resource files", it)) },
            { torStarter().send(data) })
    channel.cancel()
}

fun torStarter() = actor<TorStartData>(CommonPool) {
    val data = channel.receive()
    data.event.send(EventMessage("Starter: " + data.torConfig.torrcFile.absolutePath))

    val config = data.torConfig
    val processBuilder = ProcessBuilder(runTorArgs(config.torExecutableFile, config.torrcFile))
    val process = processBuilder.start()

    if (!process.isAlive) {
        val baos = ByteArrayOutputStream()
        process.errorStream.copyTo(baos)
        data.event.send(EventError("Tor process failed to start:" + baos.toString()))
    }

    val time = currentTimeMillis()
    process.inputStream.bufferedReader().lines().forEach {
        if (time + 30000 < currentTimeMillis()) {
            async {
                data.event.send(EventError("Tor process to too long to start"))
                process.destroy()
            }
            return@forEach
        }
        when {
            it.contains("Bootstrapped ") -> {
                async {
                    val result = boostrapRegex.find(it)
                    val progress = result!!.groups[1]!!.value.toDouble()
                    if(progress == 100.toDouble())
                        data.event.send(StartOk("OK", process))
                    else
                        data.event.send(Bootstrap(progress))

                }
                return@forEach
            }
            it.contains("[err]") -> {
                async {
                    data.event.send(EventError("Tor process failed to start: $it"))
                    process.destroy()
                }
                return@forEach
            }

        }
        async {
            data.event.send(StartProcessMessage(it))
        }
    }
}
