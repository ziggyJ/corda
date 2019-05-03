package net.corda.core.utilities

import net.corda.crypto.internal.hash
import java.security.PublicKey

/** Return the bytes of the SHA-256 output for this public key. */
fun PublicKey.toSHA256Bytes(): ByteArray = this.hash.bytes
