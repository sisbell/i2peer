package org.i2peer.network.tor

import com.goterl.lazycode.lazysodium.SodiumJava
import com.goterl.lazycode.lazysodium.LazySodiumJava
import com.goterl.lazycode.lazysodium.interfaces.Box
import org.junit.jupiter.api.Test
import com.goterl.lazycode.lazysodium.interfaces.SecretBox




class KeyTest {
    @Test
    fun a() {
        // Let's initialise LazySodium
        val lazySodium = LazySodiumJava(SodiumJava())
        val secretBoxLazy = lazySodium as Box.Lazy
      //  cryptoBoxLazy = lazySodium as Box.Lazy

        val seed = ByteArray(Box.CURVE25519XSALSA20POLY1305_SEEDBYTES)
        val keys = secretBoxLazy.cryptoBoxSeedKeypair(seed)
        //val n = Box.Native

      //  val key = secretBoxLazy.cryptoSecretBoxKeygen()
        println(keys.publicKeyString)
        println(keys.publicKey.size)
    }
}