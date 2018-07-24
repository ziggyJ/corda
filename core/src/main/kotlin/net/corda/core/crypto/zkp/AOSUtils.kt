package net.corda.core.crypto.zkp

import net.corda.core.crypto.newSecureRandom
import net.corda.core.utilities.toHex
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ThreadLocalRandom

// TODO: allow variant size per ring.
/** Generate random keys required to test Borromean signatures. */
fun generateRandomAOSKeys(g: ECPoint, ringSize: Int): AOSKeys {
    val index = ThreadLocalRandom.current().nextInt(ringSize)
    val privateKeys = List(ringSize) { BigInteger(256, newSecureRandom()) }
    val publicKeys = privateKeys.map { g.multiply(it) }
    return AOSKeys(publicKeys, index, privateKeys[index])
}

/** A data structure to store public keys and private Borromean keys. */
data class AOSKeys(val publicKeys: List<ECPoint>, val index: Int, val privateKey: BigInteger)

/** The actual Borromean signature object, consisting of e0 and s values.*/
data class AOSSignature(val chk0: BigInteger, val si: List<BigInteger?>, val y: ECPoint) {
    override fun toString(): String {
        val s = StringBuffer ("AOSSignature:\nchk0 = ${chk0.toString(16)}\nSi list =")
        s.append("[")
        si.forEach { s.append("\n${it}") }
        s.append("]")
        return s.toString()
    }
}
