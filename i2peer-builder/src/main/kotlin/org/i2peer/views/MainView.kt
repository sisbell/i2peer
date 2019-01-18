package org.i2peer.views

import com.google.common.collect.Lists
import javafx.scene.control.TextField
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import org.i2peer.Styles
import org.i2peer.auth.NoAuthInfo
import org.i2peer.network.*
import org.i2peer.network.tor.*
import tornadofx.*
import java.io.File
import java.net.ServerSocket
import java.net.Socket

var onionField: TextField by singleAssign()

var portField: TextField by singleAssign()

var proxyField: TextField by singleAssign()

var messageField: TextField by singleAssign()

lateinit var perfectActor: SendChannel<EventTask>

lateinit var fairLossActor: SendChannel<EventTask>

lateinit var stubbornActor: SendChannel<EventTask>

/**
 * Test App to Verify Connections. Installs Tor, Sets up onion. Sets up port to listen for communicationsPacket
 */
class MainView : View("Controller") {
    var onionAddress: String = ""

    override val root = borderpane {
        addClass(Styles.welcomeScreen)
        top {
            stackpane {
                label(title).addClass(Styles.heading)
            }
        }

        center {
            vbox {
                addClass(Styles.content)
                portField = textfield()// listener to receive messages
                proxyField = textfield()//tor proxy -socks
                button("Start Services") {
                    setOnAction {
                        GlobalScope.async {
                            start(proxyField.characters.toString().toInt())
                        }
                    }
                }

                onionField = textfield()//send to address
                messageField = textfield()
                button("Send Message") {
                    setOnAction {
                        GlobalScope.async {
                            println(onionField.characters)
                            val process = Process("myId", onionField.characters.toString(), "i2peer://send_message")
                            val message = Message(4, messageField.characters.toString().toByteArray())
                            val communications = CommunicationsPacket(onionAddress, process, NoAuthInfo(), System.currentTimeMillis(), message)
                           perfectActor.sendCommunications(communications)
                        }
                    }
                }
            }
        }
    }

    suspend fun start(socksPort: Int) {
        println("Start")
        val torConfig = TorConfig(socksPort = socksPort, configDir = File("mydir/" + Math.random()))
        val networkContext = NetworkContext(torConfig)

        fairLossActor= fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        stubbornActor = stubbornPointToPoint(fairLossActor,30000, Lists.newArrayList(AnyCommunicationTaskMatcher()))
        perfectActor = perfectPointToPoint(stubbornActor, Lists.newArrayList(AnyCommunicationTaskMatcher()))

        GlobalScope.async {
            ServerSocket(portField.characters.toString().toInt()).deliverCommunications(fairLossActor)
        }

        networkContext.addActorChannel(TOR_CONTROL_TRANSACTION, GlobalScope.actor<Any> {
            for (message in channel) {
                val m = message as TorControlTransaction
                if (m.request is AddOnion) {
                    val param = m.response.body as Map<String, String>
                    val serviceId = param.get("ServiceID")
                    onionAddress = serviceId + "onion"
                    //    onionField.text = serviceId + ".onion"
                    //    NetworkContext.addActorChannel(param.getActorChannels("ServiceID"))
                    println("SERVICEID:" + param.get("ServiceID"))
                    println("Message:" + m.response.code + ":" + m.response.body)
                } else {
                    println("Not Add Onion")
                }
            }
        })

        startTor(torConfig, eventChannel(networkContext))
    }

    fun eventChannel(networkContext: NetworkContext) = GlobalScope.actor<Any> {
        for (message in channel) {
            when (message) {
                is StartOk -> {
                    val socket = Socket("127.0.0.1", networkContext.torConfig.controlPortAuto())
                    val source = IO.source(socket.getInputStream())
                    val sink = IO.sink(socket.getOutputStream())
                    val torchannel = TorControlChannel(source, sink, networkContext)
                    torchannel.authenticate()//simple auth
                    torchannel.addOnion(
                            keyType = AddOnion.KeyType.NEW, keyBlob = "ED25519-V3",
                            flags = listOf(AddOnion.OnionFlag.Detach),
                            ports = listOf(AddOnion.Port(80, "127.0.0.1:" + portField.characters.toString()))
                    )
                   // torchannel.saveConfiguration(true)
                }
                else -> {
                    println(message)
                }
            }
        }
    }
}