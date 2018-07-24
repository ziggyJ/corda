package net.corda.core.crypto.zkp

import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

object AOS {

    fun sign(n: BigInteger, g: ECPoint, message: ByteArray, publicKeys: List<ECPoint>, index: Int, privateKey: BigInteger): AOSSignature {
        val ringSize = publicKeys.size
        val h = g.multiply(computehL(publicKeys))
        val y = h.multiply(privateKey)
        val m = calcM(message, y, publicKeys)
        val u = randomBigInteger()
        val chkplus1 = computeCh(m, g.multiply(u), h.multiply(u))

        val sis = kotlin.arrayOfNulls<BigInteger>(publicKeys.size)
        var chk = chkplus1
        for (i in index + 1 until ringSize) {
            sis[i] = randomBigInteger()
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk = computeCh(m,
                    g.multiply(sis[i]).add(publicKeys[i].multiply(chk)),
                    h.multiply(sis[i]).add(y.multiply(chk))
            )
        }

        val chk0 = chk
        for (i in 0 until index) {
            sis[i] = randomBigInteger()
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk = computeCh(m,
                    g.multiply(sis[i]).add(publicKeys[i].multiply(chk)),
                    h.multiply(sis[i]).add(y.multiply(chk))
            )
        }
        sis[index] = u.subtract(privateKey.multiply(chk)).mod(n)
        return AOSSignature(chk0, sis.toList(), y)
    }

    fun verify(g: ECPoint, message: ByteArray, publicKeys: List<ECPoint>, aosSignature: AOSSignature) {
        val m = calcM(message, aosSignature.y, publicKeys)
        val h = g.multiply(computehL(publicKeys))
        var chk2 = aosSignature.chk0
        for (i in 0 until aosSignature.si.size) {
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk2 = computeCh(m,
                    g.multiply(aosSignature.si[i]).add(publicKeys[i].multiply(chk2)),
                    h.multiply(aosSignature.si[i]).add(aosSignature.y.multiply(chk2))
            )
        }
        require(aosSignature.chk0 == chk2)
    }

    private fun randomBigInteger() = BigInteger(256, newSecureRandom())

    private fun calcM(m: ByteArray, y: ECPoint, publicKeys: List<ECPoint>): ByteArray {
        // TODO: use hash update vs concatentation.
        return (m + y.getEncoded(true) + concatKeysToByteArray(publicKeys)).sha256().bytes
    }

    private fun computehL(ecPoints: List<ECPoint>): BigInteger {
        return BigInteger(concatKeysToByteArray(ecPoints).sha256().bytes)
    }

    private fun computeCh(m: ByteArray, c1: ECPoint, c2: ECPoint): BigInteger {
        return BigInteger((m + c1.getEncoded(true) + c2.getEncoded(true)).sha256().bytes)
    }

    private fun concatKeysToByteArray(ecPoints: List<ECPoint>): ByteArray {
        var bytes = ByteArray(0)
        ecPoints.forEach { bytes += it.getEncoded(true) }
        return bytes
    }
}
