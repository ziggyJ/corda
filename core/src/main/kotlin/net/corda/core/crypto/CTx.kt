package net.corda.core.crypto

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey

class CTx (val x9ECParameters: X9ECParameters, val randomBase: ECPoint) {

    @Transient
    val curve = x9ECParameters.curve
    @Transient
    val n = x9ECParameters.n

    // val x9 = NISTNamedCurves.getByName("P-256") // or whatever curve you want to use
    // val n: BigInteger = x9.n // ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
    // val h: BigInteger = x9.h // co-factor
    // val g: ECPoint = x9.g // base point G
    // val o = curve.order // this is n
    // val q = curve.field.characteristic // ffffffff00000001000000000000000000000000ffffffffffffffffffffffff
    // val f = curve.fieldSize // 256.

    /**
     * Create a Pedersen commitment [ECPoint].
     * @param r: the blinding factor.
     * @param v: the committed value.
     */
    fun pedersenCommitment(r: BigInteger, v: BigInteger): ECPoint {
        return x9ECParameters.g.multiply(r).add(randomBase.multiply(v))
    }

    fun createRingPublicKeyPoints(commitmentPoint: ECPoint, ringBits: Int): List<ECPoint> {
        val listOfKeys = mutableListOf(commitmentPoint)

        for (i in 0 until ringBits - 1) {
            listOfKeys.add(commitmentPoint.subtract(randomBase.multiply(BigInteger.valueOf(2).pow(i))))
        }
        return listOfKeys
    }

    fun createAllRingPublicKeyPoints(commitmentPoint: ECPoint, maxValue: Int): List<ECPoint> {
        val listOfKeys = mutableListOf(commitmentPoint)

        for (i in 1..maxValue) {
            listOfKeys.add(commitmentPoint.subtract(randomBase.multiply(BigInteger.valueOf(i.toLong()))))
        }
        return listOfKeys
    }

    fun generatePublicKey(publicKeyPoint: ECPoint): PublicKey {
        val spec = ECParameterSpec(curve, x9ECParameters.g, x9ECParameters.n)
        val publicSpec = ECPublicKeySpec(publicKeyPoint, spec)
        val keyFactory = KeyFactory.getInstance("ECDSA", "BC")
        return keyFactory.generatePublic(publicSpec)
    }

    fun aosComputehOpt(ecPoint: ECPoint): ECPoint {
        val hL = BigInteger(ecPoint.getEncoded(true).sha256().bytes).mod(n)
        return this.x9ECParameters.g.multiply(hL)
    }

    // h
    fun aosComputeh(publicKeyPoints: List<ECPoint>): ECPoint {
        val hL = BigInteger(concatKeysToByteArray(publicKeyPoints).sha256().bytes).mod(n)
        return this.x9ECParameters.g.multiply(hL)
    }

    // y
    fun aosComputey(xk: BigInteger, hPoint: ECPoint): ECPoint {
        return hPoint.multiply(xk)
    }

    // L,y,m
    fun aosFirstHashPart(L: List<ECPoint>, y: ECPoint, m: ECPoint): ByteArray {
        return concatKeysToByteArray(L.toMutableList() + y + m)
    }

    fun aosFirstChallengeOpt(y: ECPoint, m: ECPoint, u: BigInteger, h: ECPoint): BigInteger {
        val hashInput = concatKeysToByteArray(y, m, x9ECParameters.g.multiply(u), h.multiply(u)).sha256().bytes
        return BigInteger(hashInput).mod(n)
    }

    // ch(k+1)
    fun aosFirstChallenge(L: List<ECPoint>, y: ECPoint, m: ECPoint, u: BigInteger, h: ECPoint): BigInteger {
        val hashInput = concatKeysToByteArray(
                L.toMutableList()
                        + y
                        + m
                        + x9ECParameters.g.multiply(u)
                        + h.multiply(u)
        ).sha256().bytes
        return BigInteger(hashInput).mod(n)
    }


    fun aosOtherChallengesOpt(y: ECPoint, m: ECPoint, aPoint: ECPoint, bPoint: ECPoint): BigInteger {
        val hashInput = concatKeysToByteArray(y, m, aPoint, bPoint).sha256().bytes
        return BigInteger(hashInput).mod(n)
    }

    // ch(i)
    fun aosOtherChallenges(L: List<ECPoint>, y: ECPoint, m: ECPoint, aPoint: ECPoint, bPoint: ECPoint): BigInteger {
        val hashInput = concatKeysToByteArray(
                L.toMutableList()
                        + y
                        + m
                        + aPoint
                        + bPoint
        ).sha256().bytes
        return BigInteger(hashInput).mod(n)
    }

    // sk
    fun aosSk(u: BigInteger, xk: BigInteger, chk: BigInteger): BigInteger {
        return u.subtract(xk.multiply(chk)).mod(n)
    }

    private fun concatKeysToByteArray(ecPoints: List<ECPoint>): ByteArray {
        var bytes = ByteArray(0)
        ecPoints.forEach { bytes += it.getEncoded(true) }
        return bytes
    }

    private fun concatKeysToByteArray(vararg ecPoints: ECPoint): ByteArray {
        var bytes = ByteArray(0)
        ecPoints.forEach { bytes += it.getEncoded(true) }
        return bytes
    }
}

data class ctxSignature(val chk0: BigInteger, val si: List<BigInteger?>, val y: ECPoint)
