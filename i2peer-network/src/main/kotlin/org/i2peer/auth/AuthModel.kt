package org.i2peer.auth

import org.i2peer.network.AuthInfo

/**
 * Temporary token that is generated when the user initiates an authentication
 * flow. This token should be persisted upon creation. After being used to
 * generate a session or after a period of time after creation, it can be
 * deleted.
 */
data class TempToken(val token: String, val timestamp: Long, val isUsed: Boolean )

data class AccessToken(val token: String, val timestamp: Long)

/**
 * Provide an address and temporary token for authentication. This will be
 * returned by the server to the requesting client as the first step in an
 * authentication flow.
 */
data class TempTokenResponse(val token: String, val scope: String)

data class Claims(val id: String, val issuer: String, val audience: String, val scope: String, val issueTimestamp: Long, val expireTimestamp: Long)

class NoAuthInfo : AuthInfo(0)

class BasicAuthInfo(val username: String, val password: String) : AuthInfo(1)

class TokenAuthInfo(val publicKey: String, val sessionToken: String) : AuthInfo(2)

class UnsupportedAuthInfo() : AuthInfo(-1)


