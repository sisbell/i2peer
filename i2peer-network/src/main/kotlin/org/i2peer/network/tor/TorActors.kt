package org.i2peer.network.tor

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.runBlocking
import org.i2peer.network.createDir
import org.i2peer.network.setPermsRwx
import org.i2peer.network.tor.Installer.copyFiles
import org.i2peer.network.tor.Installer.unzipArchive
import org.i2peer.network.tor.TorConfig.Companion.writeConfig
import java.io.ByteArrayOutputStream
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit

data class TorStartData(val torConfig: TorConfig, val event: SendChannel<Any>)
data class EventMessage(val text: String)
data class EventError(val message: String, val e: Throwable? = null)
data class StartProcessMessage(val message: String)
data class StartOk(val message: String, val torProcess: Process)
data class Bootstrap(val percentage: Double)

@ObsoleteCoroutinesApi
suspend fun startTor(torConfig: TorConfig, event: SendChannel<Any>) =
    torConfigWriter().send(TorStartData(torConfig, event))

@ObsoleteCoroutinesApi
fun torConfigWriter() = GlobalScope.actor<TorStartData> {
    val data = channel.receive()
    data.event.send(EventMessage("TorConfigWriter"))

    if (data.torConfig.dataDir.createDir()) {
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

@ObsoleteCoroutinesApi
fun torInstallNeededChecker() = GlobalScope.actor<TorStartData> {
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

@ObsoleteCoroutinesApi
fun torProgramUnarchiver() = GlobalScope.actor<TorStartData> {
    val data = channel.receive()
    data.event.send(EventMessage("TorUnarchiver"))

    val config = data.torConfig
    unzipArchive(config.torExecutableFile).fold(
        { data.event.send(EventError("Failed to unzip tor archive", it)) },
        {
            config.torExecutableFile.setPermsRwx()
                .fold({ data.event.send(EventError("Failed to set permissions on tor", it)) },
                    { torConfigFilesCopier().send(data) })
        })
    channel.cancel()
}

@ObsoleteCoroutinesApi
fun torConfigFilesCopier() = GlobalScope.actor<TorStartData> {
    val data = channel.receive()
    data.event.send(EventMessage("Copier"))

    val config = data.torConfig
    copyFiles(geoIpFile = config.geoIpFile, geoIpv6File = config.geoIpv6File)
        .fold({ data.event.send(EventError("Failed to copy resource files", it)) },
            { torStarter().send(data) })
    channel.cancel()
}

@ObsoleteCoroutinesApi
fun torStarter() = GlobalScope.actor<TorStartData> {
    val data = channel.receive()
    data.event.send(EventMessage("Starter: " + data.torConfig.torrcFile.absolutePath))

    val config = data.torConfig
    val processBuilder = ProcessBuilder(runTorArgs(config.torExecutableFile, config.torrcFile))
    val environment = processBuilder.environment()
    environment.put("LD_LIBRARY_PATH", config.libraryPath.absolutePath)

    val process = processBuilder.start()
    process.waitFor(5000, TimeUnit.MILLISECONDS)

    if (!process.isAlive) {
        val baos = ByteArrayOutputStream()
        process.errorStream.copyTo(baos)
        println("ERROR:" + baos.toString())
        data.event.send(EventError("Tor targetProcess failed to start:"))
        return@actor
    }

    // targetProcess.errorStream.bufferedReader().lines().forEach {
    //     System.out.println("Line: " + it)
    //}

    val time = currentTimeMillis()

    process.inputStream.bufferedReader().lines().forEach {
        /*
        if (time + 30000 < currentTimeMillis()) {
            data.event.offer(EventError("Tor targetProcess taking too long to start"))
            process.destroy()
        }
*/
        when {
            it.contains("Bootstrapped ") -> {
                val result = boostrapRegex.find(it)
                val progress = result!!.groups[1]!!.value.toDouble()
                runBlocking {
                    if (progress == 100.toDouble()) {
                        println("Start OK")
                        data.event.send(StartOk("OK", process))
                    } else
                        data.event.send(Bootstrap(progress))
                }

                return@forEach
            }
            it.contains("[err]") -> {
                data.event.offer(EventError("Tor targetProcess failed to start: $it"))
                process.destroy()
            }
        }

        System.out.println(it)
        data.event.offer(StartProcessMessage(it))
    }
}
