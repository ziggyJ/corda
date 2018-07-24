package net.corda.core.crypto.zkp

import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/** Borromean signature implementation. */
object Borromean {

    /** Sign using the Borromean Signature scheme, as described in Maxwell's paper. */
    fun sign(n: BigInteger, g: ECPoint, message: ByteArray, publicKeys: List<List<ECPoint>>, indices: List<Int>, privateKeys: List<BigInteger>): BorromeanSignature {
        val numOfRings = indices.size
        // Compute M.
        val m = calcM(message, publicKeys)
        val sList = initSList(publicKeys)
        val kList = initKList(numOfRings)
        var e0 = m // We init with M.
        // Step2.
        var rij: ECPoint
        var eij: BigInteger
        for (i in 0 until numOfRings) {
            rij = g.multiply(kList[i])
            for (j in (indices[i] + 1) until (publicKeys[i].size)) {
                eij = computeEij (m, rij.getEncoded(true), i, j)
                rij = g.multiply(sList[i][j]).add(publicKeys[i][j].multiply(eij))
            }
            // TODO: use digest.update vs concatentation.
            e0 += rij.getEncoded(true) // Append to rij.
        }
        // Step 3.
        e0 = e0.sha256().bytes

        // Step 4.
        for (i in 0 until numOfRings) {
            eij = computeEij (m, e0, i, 0)
            for (j in 0 until indices[i]) {
                rij = g.multiply(sList[i][j]).add(publicKeys[i][j].multiply(eij))
                eij = computeEij (m, rij.getEncoded(true), i, j + 1)
            }
            sList[i][indices[i]] = (kList[i] - (privateKeys[i].multiply(eij))).mod(n)
        }
        return BorromeanSignature(e0, sList)
    }

    /** Verify a [BorromeanSignature]. */
    fun verify(g: ECPoint, message: ByteArray, publicKeys: List<List<ECPoint>>, borromeanSig: BorromeanSignature) {
        val m = calcM(message, publicKeys)
        var rij: ECPoint = g // Just to initialise.
        var eij: BigInteger
        var e0Verify = m
        for (i in 0 until publicKeys.size) {
            eij = computeEij (m, borromeanSig.e0, i, 0)
            for (j in 0 until publicKeys[i].size) {
                rij = g.multiply(borromeanSig.si[i][j]).add(publicKeys[i][j].multiply(eij))
                eij = computeEij (m, rij.getEncoded(true), i, j + 1)
            }
            e0Verify += rij.getEncoded(true)
        }
        require(borromeanSig.e0.contentEquals(e0Verify.sha256().bytes))
    }

    private fun calcM(m: ByteArray, publicKeys: List<List<ECPoint>>): ByteArray {
        // TODO: use hash update vs concatentation.
        return (m + concatKeysToByteArray(publicKeys)).sha256().bytes
    }

    private fun concatKeysToByteArray(ecPoints: List<List<ECPoint>>): ByteArray {
        var bytes = ByteArray(0)
        ecPoints.forEach { it.forEach { bytes += it.getEncoded(true) } }
        return bytes
    }

    // Create random s List (Rings) of Lists (keys per ring).
    private fun <T> initSList(anotherListList: List<List<T>>): List<MutableList<BigInteger>> {
        // TODO: do not initialise sValues that will be computed later on.
        return List(anotherListList.size) { MutableList(anotherListList[it].size) { BigInteger(256, newSecureRandom()) } }
    }

    // Create random k List.
    private fun initKList(numberOfRings: Int): List<BigInteger> {
        return List(numberOfRings) { BigInteger(256, newSecureRandom()) }
    }

    private fun computeEij(M: ByteArray, rijBytes: ByteArray, i: Int, j: Int): BigInteger {
        // TODO: use real Int representation vs Int.toByte() or cache all i,j combinations.
        //      Also, use hash update and not concatenate them first.
        return BigInteger((M + rijBytes + i.toByte() + j.toByte()).sha256().bytes)
    }
}
