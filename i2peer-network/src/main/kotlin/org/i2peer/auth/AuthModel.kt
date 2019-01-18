package org.i2peer.auth

import org.i2peer.network.AuthInfo

/**
 * Temporary token that is generated when the user initiates an authentication
 * flow. This token should be persisted upon creation. After being used to
 * generate a session or after a period of time after creation, it can be
 * deleted.
 */
data class TempToken(val token: String, val timestamp: Long, val isUsed: Boolean)

data class AccessToken(val token: String, val timestamp: Long)

/**
 * Provide an address and temporary token for authentication. This will be returned by the server to the requesting
 * client as the first step in an authentication flow.
 */
data class TempTokenResponse(val token: String, val scope: String)

data class Claims(
    val id: String,
    val issuer: String,
    val audience: String,
    val scope: String,
    val issueTimestamp: Long,
    val expireTimestamp: Long
)

class NoAuthInfo : AuthInfo(0) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

/**
 * Authentication info that requires [username] and [password]
 */
class BasicAuthInfo(val username: String, val password: String) : AuthInfo(1) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BasicAuthInfo

        if (username != other.username) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }
}

/**
 * Authentication info that requires the user's [publicKey] as a permanent identity and a [sessionToken] for
 * authenticated access to resources. The [sessionToken] can be expired or invalidated by the issuer.
 */
class TokenAuthInfo(val publicKey: String, val sessionToken: String) : AuthInfo(2) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenAuthInfo

        if (publicKey != other.publicKey) return false
        if (sessionToken != other.sessionToken) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + sessionToken.hashCode()
        return result
    }
}

class UnsupportedAuthInfo : AuthInfo(-1)


