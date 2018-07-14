package org.i2peer.views

import javafx.scene.control.TextField
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
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

var thread: Thread by singleAssign()

/**
 * Test App to Verify Connections. Installs Tor, Sets up onion. Sets up port to listen for communicationsPacket
 */
class MainView : View("Controller") {
    var onionAddress: String = ""

    override val root = borderpane {
        addClass(Styles.welcomeScreen)
        top {
            stackpane {
                menubar {
                    menu("File") {
                        menu("Connect") {
                            item("A")
                            item("B")
                        }
                        item("Save")
                        item("Quit")
                    }
                    menu("Edit") {
                        item("Copy")
                        item("Paste")
                    }
                    menu("Bookmark") {

                    }
                    menu("History") {

                    }
                    menu("Tools") {

                    }
                }
                label(title).addClass(Styles.heading)
            }
        }


        center {
            vbox {
                addClass(Styles.content)
                portField = textfield()// listener to receive messages
                button("Start Server") {
                    setOnAction {
                       launch {
                            ServerSocket(portField.characters.toString().toInt()).deliverCommunications()
                        }
                    }
                }

                proxyField = textfield()//tor proxy -socks
                button("Start T") {
                    setOnAction {
                        async {
                            start()
                        }
                    }
                }

                onionField = textfield()//send to address
                messageField = textfield()
                button("Send Message") {
                    setOnAction {
                        async {
                            println(onionField.characters)
                            val process = Process("myId", onionField.characters.toString(), "i2peer://send_message")
                            val message = Message(4, messageField.characters.toString().toByteArray())
                            val communications = CommunicationsPacket(onionAddress, process, NoAuthInfo(), System.currentTimeMillis(), message)
                            perfectPointToPoint().sendCommunications(communications)
                        }
                    }
                }
            }
        }

        ActorRegistry.add(ActorRegistry.TOR_CONTROL_TRANSACTION, actor<Any> {
            for (message in channel) {
                val m = message as TorControlTransaction
                if (m.request is AddOnion) {
                    val param = m.response.body as Map<String, String>
                    val serviceId = param.get("ServiceID")
                    onionAddress = serviceId + "onion"
                //    onionField.text = serviceId + ".onion"
                    //    TorContext.add(param.get("ServiceID"))
                    println("SERVICEID:" + param.get("ServiceID"))
                    println("Message:" + m.response.code + ":" + m.response.body)
                } else {
                    println("Not Add Onion")
                }
            }
        })

    }

    suspend fun start() {
        val torConfig = TorConfig(socksPort = proxyField.characters.toString().toInt(), configDir = File("mydir/" + Math.random()))
        org.i2peer.network.config = torConfig
        startTor(torConfig, startActor(torConfig))
    }

    fun startActor(torConfig: TorConfig) = actor<Any> {
        for (message in channel) {
            when (message) {
                is StartOk -> {
                    val socket = Socket("127.0.0.1", torConfig.controlPortAuto())
                    val source = IO.source(socket.getInputStream())
                    val sink = IO.sink(socket.getOutputStream())
                    val torchannel = TorControlChannel(source, sink)
                    torchannel.authenticate()//simple auth
                    torchannel.addOnion(
                            keyType = AddOnion.KeyType.NEW, keyBlob = "ED25519-V3",
                            ports = listOf(AddOnion.Port(80, "127.0.0.1:" + portField.characters.toString()))
                    )
                }
                else -> {
                    println(message)
                }
            }
        }
    }

}