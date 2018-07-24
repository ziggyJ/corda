package net.corda.core.crypto.zkp

import net.corda.core.crypto.newSecureRandom
import net.corda.core.utilities.toHex
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ThreadLocalRandom

// TODO: allow variant size per ring.
/** Generate random keys required to test Borromean signatures. */
fun generateRandomBorromeanKeys(g: ECPoint, numOfRings: Int = 1, keysPerRing: Int = 2): BorromeanKeys {
    val indices = List(numOfRings) { ThreadLocalRandom.current().nextInt(keysPerRing) }
    val privateKeys = mutableListOf<BigInteger>()
    val publicKeys = mutableListOf<List<ECPoint>>()
    for (i in 0 until numOfRings) {
        val privateK = List(keysPerRing) { BigInteger(256, newSecureRandom()) }
        privateKeys.add(privateK[indices[i]])
        publicKeys.add(privateK.map { g.multiply(it) })
    }
    return BorromeanKeys(publicKeys, indices, privateKeys)
}

/** A data structure to store public keys and private Borromean keys. */
data class BorromeanKeys(val publicKeys: List<List<ECPoint>>, val indices: List<Int>, val privateKeys: List<BigInteger>)

/** The actual Borromean signature object, consisting of e0 and s values.*/
data class BorromeanSignature(val e0: ByteArray, val si: List<List<BigInteger>>) {
    override fun toString(): String {
        val s = StringBuffer ("BorromeanSignature:\ne0 = ${e0.toHex()}\nSi list = ")
        si.forEach { s.append("\n${printList(it)}") }
        return s.toString()
    }

    private fun printList(array: List<BigInteger>): String {
        val s = StringBuffer("[")
        array.forEach { s.append(it.toString(16) + ",\n") }
        s.setLength(s.length - 2)
        s.append("]")
        return s.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BorromeanSignature

        if (!Arrays.equals(e0, other.e0)) return false
        if (si != other.si) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(e0)
        result = 31 * result + si.hashCode()
        return result
    }
}
