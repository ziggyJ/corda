package net.corda.crypto.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import java.security.PublicKey

val PublicKey.hash: SecureHash get() = encoded.sha256()

