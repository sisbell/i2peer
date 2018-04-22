# i2peer
Distributed Protocols Built Over the Tor Network

Starting Tor is simple. As part of the start process, it will install Tor if needed.

    suspend fun start() {
        val torConfig = TorConfig(configDir = File("mydir"))
        startTor(torConfig, startActor(torConfig))
    }

You will need to create an actor, which will receive various messages from the start process. The sample below waits for
the start OK message and then proceeds to connect to the TorControl Channel. It then adds an Onion to the service.

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
                        keyType = AddOnion.KeyType.NEW, keyBlob = "BEST",
                        ports = listOf(AddOnion.Port(80, "127.0.0.1:80"))
                    )
                } else -> {
                    println(message)
                }
            }
        }
    }
