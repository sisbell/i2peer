package org.i2peer.network.tor

import arrow.core.Try
import com.google.common.io.BaseEncoding
import java.security.MessageDigest
import java.security.SecureRandom

class PasswordDigest(val secret: ByteArray = secret(20), private val specifier: ByteArray = defaultSpecifier()) {

    fun hashedPassword(): Try<String> = Try { "16:" + secretToKey(secret, specifier).flatMap { encodeBytes(it) } }

    companion object {
        private fun secretToKey(secret: ByteArray, specifier: ByteArray): Try<ByteArray> {
            return Try {
                var count = count(specifier[8].toInt() and 0xff)
                val tmp = specifier.copyOfRange(0, 8) + secret
                val messageDigest = MessageDigest.getInstance("SHA-1")

                while (count > 0 && count >= tmp.size) {
                    messageDigest.update(tmp)
                    count -= tmp.size
                }
                messageDigest.update(tmp, 0, count)
                specifier + messageDigest.digest()
            }
        }

        private fun encodeBytes(bytes: ByteArray): Try<String> = Try { BaseEncoding.base16().encode(bytes) }

        private fun secret(size: Int): ByteArray {
            val secret = ByteArray(size)
            SecureRandom().nextBytes(secret)
            return secret
        }

        private fun defaultSpecifier(): ByteArray {
            val specifier = secret(9)
            specifier[8] = 96
            return specifier
        }

        private fun count(c: Int) = 16 + (c and 15) shl (c shr 4) + 6

    }
}