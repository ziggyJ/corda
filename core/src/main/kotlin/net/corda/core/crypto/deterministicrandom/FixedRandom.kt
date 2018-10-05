package net.corda.core.crypto.deterministicrandom

import java.security.SecureRandom
import java.util.Random

/**
 * A SecureRandom implementation that always returns the same bytes.
 * Note that the property of always returning the same bytes might cause an infinite loop in applications
 * that require sampling or multiple random outcomes, i.e., [Random.nextGaussian] is such a case.
 * WARNING: DO NOT USE IT IN PRODUCTION for random number generation.
 */
class FixedRandom : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        for (i in bytes.indices) {
            bytes[i] = (i and 0xff).toByte()
        }
    }
}
