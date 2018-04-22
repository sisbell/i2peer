package org.i2peer.network

import java.util.*

object CodeGenerator {

    val alphabet = "abcdefghijklmnopqrstuvxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val alphabet2 = "abcdefghijklmnopqrstuvxyz0123456789"

    val size = alphabet.length

    fun generateCode(): String {
        val r = Random()
        val sb = StringBuilder()
        for (i in 0..7) sb.append(alphabet[r.nextInt(size)])
        return sb.toString()
    }

    fun generateCode(s: Int): String {
        val r = Random()
        val sb = StringBuilder()
        for (i in 0 until s) sb.append(alphabet[r.nextInt(size)])
        return sb.toString()
    }

    fun generateID(): String {
        val r = Random()
        val sb = StringBuilder()
        for (i in 0..31) sb.append(alphabet2[r.nextInt(size)])
        return sb.toString()
    }
}