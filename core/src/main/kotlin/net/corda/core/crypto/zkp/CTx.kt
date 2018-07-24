package net.corda.core.crypto.zkp

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

    fun createRingPublicKeyPointsBinary(commitmentPoint: ECPoint, ringBits: Int): List<ECPoint> {
        val listOfKeys = mutableListOf(commitmentPoint)

        for (i in 0 until ringBits - 1) {
            listOfKeys.add(commitmentPoint.subtract(randomBase.multiply(BigInteger.valueOf(2).pow(i))))
        }
        return listOfKeys
    }

    fun createAllRingPublicKeyPoints(commitmentPoint: ECPoint, maxValue: Int): List<ECPoint> {
        val listOfKeys = mutableListOf(commitmentPoint)
        for (i in 1 until maxValue) {
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
}
