package org.i2peer.network.tor

import arrow.core.Try
import arrow.core.fix
import arrow.core.getOrElse
import arrow.core.monad
import arrow.typeclasses.binding
import org.i2peer.network.Files
import org.i2peer.network.Files.copyResource
import org.i2peer.network.OsType
import org.i2peer.network.osType
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

const val GEO_IP_NAME = "geoip"
const val GEO_IPV_6_NAME = "geoip6"
const val TORRC_NAME = "torrc"
const val HIDDEN_SERVICE_NAME = "hiddenservice"

data class ReplyLine(val status: Int, val msg: String, val rest: String?)

data class TorConfig(
    val configDir: File,
    val userHome: String? = System.getProperty("user.home"),
    val torExecutableFile: File = File(configDir, torExecutableFileName),
    val geoIpFile: File = File(configDir, GEO_IP_NAME),
    val geoIpv6File: File = File(configDir, GEO_IPV_6_NAME),
    val torrcFile: File = File(configDir, TORRC_NAME),
    val hiddenServiceDir: File = File(configDir, HIDDEN_SERVICE_NAME),
    val dataDir: File = File(configDir, "lib/tor"),
    val hostnameFile: File = File(dataDir, "hostname"),
    val cookieAuthFile: File = File(dataDir, "control_auth_cookie"),
    val libraryPath: File = torExecutableFile.parentFile,
    val controlPort: Int = 0,
    val controlPortFile: File = File(dataDir, "control.txt")
) {

    fun controlPortAuto() = controlPortFile.readText().split(":")[1].trim().toInt()

    companion object {

        fun createFlatConfig(configDir: File): TorConfig = TorConfig(configDir = configDir, dataDir = configDir)

        fun writeConfig(torConfig: TorConfig): Try<Boolean> {
            return Try {
                torConfig.torrcFile.bufferedWriter().use { out ->
                    out.write("CookieAuthFile ${torConfig.cookieAuthFile.absolutePath}\r\n")
                    out.write("PidFile " + File(torConfig.dataDir, "pid").absolutePath + "\r\n")
                    out.write("GeoIPFile ${torConfig.geoIpFile.absoluteFile}\r\n")
                    out.write("GeoIPv6File ${torConfig.geoIpv6File.absolutePath}\r\n")
                    out.write("ControlPortWriteToFile ${torConfig.controlPortFile.absolutePath}\r\n")

                    if (torConfig.controlPort == 0)
                        out.write("ControlPort auto\r\n")
                    else
                        out.write("ControlPort ${torConfig.controlPort}\r\n")
                }
                true
            }
        }

        val torExecutableFileName: String = when (osType()) {
            OsType.ANDROID, OsType.LINUX_64, OsType.MAC -> "tor"
            OsType.WINDOWS -> "tor.exe"
            else -> throw RuntimeException("Unsupported OS")
        }
    }
}

object Installer {
    fun unzipArchive(
        torExecutableFile: File,
        torArchive: InputStream? = Files.getResourceStream(getPathToTorArchive()).getOrElse { null },
        installDir: File = Files.resolveParent(torExecutableFile)
    ): Try<Unit> {
        return Try {
            val stream = ZipInputStream(torArchive)
            var entry = stream.nextEntry

            while (entry != null) {
                val f = File(installDir, entry.name)
                if (entry.isDirectory)
                    f.mkdirs()
                else
                    f.outputStream().use { output -> stream.copyTo(output) }

                stream.closeEntry()
                entry = stream.nextEntry
            }
        }
    }

    fun copyFiles(geoIpFile: File, geoIpv6File: File): Try<Long> = Try.monad().binding {
        copyResource(GEO_IP_NAME, geoIpFile).bind() + copyResource(GEO_IPV_6_NAME, geoIpv6File).bind()
    }.fix()
}

fun doResourceFilesExist(geoIpFile: File, geoIpv6File: File, torrcFile: File): Boolean =
    geoIpFile.exists() && geoIpv6File.exists() && torrcFile.exists()

fun getPathToTorArchive(osType: OsType? = osType(), parentPath: String? = "native"): String {
    return when (osType) {
        OsType.WINDOWS -> "$parentPath/windows/x86/tor.zip"
        OsType.MAC -> "$parentPath/osx/x64/tor.zip"
        OsType.LINUX_64 -> "$parentPath/linux/x64/tor.zip"
        else -> throw RuntimeException("OS Unsupported")
    }
}

fun runTorArgs(torExecutableFile: File, torrcFile: File): List<String> {
    return listOf(torExecutableFile.absolutePath, "-f", torrcFile.absolutePath)
}