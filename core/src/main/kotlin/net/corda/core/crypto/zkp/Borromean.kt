package net.corda.core.crypto.zkp

import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

object Borromean {

    fun sign(n: BigInteger, g: ECPoint, m: ByteArray, publicKeys: List<List<ECPoint>>, indices: List<Int>, privateKeys: List<BigInteger>): BorromeanSignature {
        val numOfRings = publicKeys.size
        // Step 1.
        // Compute M.
        val M = calcM(m, publicKeys)

        val sList = initSarray(publicKeys)
        val e0mList = mutableListOf<BigInteger>()
        val kiList = List(numOfRings) { BigInteger(256, newSecureRandom()) }
        // Step 2.
        for (i in 0 until numOfRings) {
            // Compute ei,j'+1
            val eij1 = computeEij1(M, kiList[i], g, i, indices[i])

            // Next ei
            var eij1plus = eij1
            for (j in indices[i] + 1 until publicKeys[i].size - 1) {
                val si = BigInteger(256, newSecureRandom())
                sList[i][j] = si
                eij1plus = computeEij1plus(M, si, g, eij1plus, publicKeys[i][j], i, j)
            }
            sList[i][sList[i].size - 1] = BigInteger(256, newSecureRandom()).mod(n)
            e0mList.add(eij1plus)
        }
        // Step 3.
        val e0 = computeE0(e0mList, sList, g, publicKeys)

        // Step 4.
        for (i in 0 until numOfRings) {
            var eij1plus = e0
            for (j in 0 until indices[i]) {
                val si = BigInteger(256, newSecureRandom()).mod(n)
                sList[i][j] = si
                eij1plus = computeEij1plus(M, si, g, eij1plus, publicKeys[i][j], i, j)
            }
            sList[i][indices[i]] = (kiList[i] + privateKeys[i].multiply(eij1plus)).mod(n)
        }

        // Return, everything is in place.
        return BorromeanSignature(e0.mod(n), sList)
    }

    fun verify(n: BigInteger, g: ECPoint, m: ByteArray, publicKeys: List<List<ECPoint>>, borromeanSignature: BorromeanSignature) {
        val M = calcM(m, publicKeys)
        val numOfRings = publicKeys.size
        var eij1plus = borromeanSignature.e0
        val riList = mutableListOf<ECPoint>()
        var rij1plus: ECPoint? = null
        for (i in 0 until numOfRings) {
            for (j in 0 until publicKeys[i].size) {
                rij1plus = g.multiply(borromeanSignature.si[i][j]).add(publicKeys[i][j].multiply(eij1plus))
                eij1plus = computeEij1plusVerify(M, rij1plus, i, j)
            }
            riList.add(rij1plus!!)
        }
        val e0New = computeE0Verify(riList)
        require(e0New.mod(n) == borromeanSignature.e0)
    }

    private fun computeEij1plusVerify(M: ByteArray, rij1plus: ECPoint, i: Int, j: Int): BigInteger{
        // TODO: use real Int representation vs Int.toByte()
        return BigInteger((M + (rij1plus).getEncoded(true) + i.toByte() + (j + 1).toByte()).sha256().bytes)
    }

    private fun computeE0Verify(riList: List<ECPoint>): BigInteger {
        var bytes = ByteArray(0)
        riList.forEach { bytes += it.getEncoded(true) }
        return BigInteger(bytes)
    }

    private fun computeE0(e0mList: List<BigInteger>, sList: List<Array<BigInteger?>>, g: ECPoint, publicKeys: List<List<ECPoint>>): BigInteger {
        var pointI: ECPoint?
        var bytes = ByteArray(0)
        for (i in 0 until e0mList.size) {
            pointI = g.multiply(sList[i][sList[i].size - 1]).subtract(publicKeys[i][publicKeys[i].size - 1].multiply(e0mList[i]))
            bytes += pointI.getEncoded(true)
        }
        return BigInteger(bytes.sha256().bytes)
    }

    private fun <T> initSarray(anotherListList: List<List<T>>): List<Array<BigInteger?>> {
        return List(anotherListList.size) { arrayOfNulls<BigInteger?>(anotherListList[it].size) }
    }

    private fun calcM(m: ByteArray, publicKeys: List<List<ECPoint>>): ByteArray {
        return (m + concatKeysToByteArray(publicKeys)).sha256().bytes
    }

    private fun concatKeysToByteArray(ecPoints: List<List<ECPoint>>): ByteArray {
        var bytes = ByteArray(0)
        ecPoints.forEach { it.forEach { bytes += it.getEncoded(true) } }
        return bytes
    }

    private fun computeEij1(M: ByteArray, ki: BigInteger, g: ECPoint, i: Int, j: Int): BigInteger {
        // TODO: use real Int representation vs Int.toByte()
        return BigInteger((M + g.multiply(ki).getEncoded(true) + i.toByte() + j.toByte()).sha256().bytes)
    }

    private fun computeEij1plus(M: ByteArray, si: BigInteger, g: ECPoint, ei: BigInteger, p: ECPoint, i: Int, j: Int): BigInteger{
        // TODO: use real Int representation vs Int.toByte()
        return BigInteger((M + (g.multiply(si).subtract(p.multiply(ei))).getEncoded(true) + i.toByte() + j.toByte()).sha256().bytes)
    }

    data class BorromeanSignature(val e0: BigInteger, val si: List<Array<BigInteger?>>) {
        override fun toString(): String {
            val s = StringBuffer ("BorromeanSignature:\ne0 = ${e0.toString(16)}\nSi list = ")
            si.forEach { s.append("\n${printArray(it)}") }
            return s.toString()
        }

        private fun printArray(array: Array<BigInteger?>): String {
            val s = StringBuffer("[")
            array.forEach { s.append(it!!.toString(16) + ",\n") }
            s.setLength(s.length - 2)
            s.append("]")
            return s.toString()
        }
    }
}