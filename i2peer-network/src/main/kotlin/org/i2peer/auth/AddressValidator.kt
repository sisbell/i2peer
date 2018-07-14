package org.i2peer.auth

import java.util.regex.Pattern

/**
 * Validates if user address or public key is of valid format
 */
object AddressValidator {

    private val addressPattern = Pattern.compile("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")

    /**
     * Returns true if the address if valid, otherwise returns false.
     *
     * @param address
     * the address or public key to validate
     * @return true if the address if valid, otherwise returns false.
     */
    fun validateAddress(address: String): Boolean {
        return addressPattern.matcher(address).matches()
    }

}