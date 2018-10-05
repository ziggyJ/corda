package net.corda.core.crypto.deterministicrandom

import net.corda.core.crypto.SecureHash
import java.security.SecureRandom

/**
 * A seeded [SecureRandom] implementation using a SHA256 hash-chain to ensure deterministic (predictable) results.
 * Note that this implementation is not thread safe, so sharing objects of this type in different threads might
 * result to not predictable sequence in the same thread. For example, a parallel implementation for finding prime
 * numbers (i.e., in RSA) does not guarantee that the output is always the same.
 * WARNING: DO NOT USE IT IN PRODUCTION, only for testing.
 * @param seed a ByteArray initial seed.
 */
class HashChainRandom @JvmOverloads constructor(seed: ByteArray = ByteArray(32)) : SecureRandom() {
    private val state = SecureHash.sha256(seed).bytes

    override fun nextBytes(bytes: ByteArray) {
        val length = bytes.size
        for (i in 0 until length / 32) {
            System.arraycopy(updatedState(), 0, bytes, i * 32, 32)
        }
        System.arraycopy(updatedState(), 0, bytes, (length / 32) * 32, length % 32)
    }

    private fun updatedState(): ByteArray {
        System.arraycopy(SecureHash.sha256(state).bytes, 0, state, 0, 32)
        return state
    }
}
