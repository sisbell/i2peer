package org.i2peer.network.tor

import java.net.InetSocketAddress

interface TorContext {

    val torConfig: TorConfig



    companion object {

        val onions = ArrayList<String>()

       // var torConfig torConfig

        /**
         * Gets all onion address configured for this node
         */
        fun localOnionAddress() : List<String> {
            return onions
        }


        fun add(onion: String) {
            onions.add(onion)
        }
    }

}