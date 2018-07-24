package net.corda.core.crypto

import net.corda.core.crypto.zkp.Borromean
import net.corda.core.crypto.zkp.generateRandomKeys
import net.corda.core.crypto.zkp.mapToPoint
import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

/** Tests fro algorithms required in Confidential Transactions. */
class CtxTests {
    @Test
    fun testMap2Point() {
        Crypto.registerProviders()
        val s = "Corda".toByteArray() // input String

        println("Mapping \"Corda\" to an elliptic curve point")

        val r1 = NISTNamedCurves.getByName("P-521")
        val r1Point = r1.curve.mapToPoint(s)

        println("P-521 x: ${r1Point.affineXCoord.toBigInteger()}")
        println("P-521 y: ${r1Point.affineYCoord.toBigInteger()}")

        val k1 = SECNamedCurves.getByName("secp256k1")
        val k1Point = k1.curve.mapToPoint(s)
        println("---")
        println("secp256k1 x: ${k1Point.affineXCoord.toBigInteger()}")
        println("secp256k1 y: ${k1Point.affineYCoord.toBigInteger()}")

        val X25519 = CustomNamedCurves.getByName("Curve25519")
        val xPoint = X25519.curve.mapToPoint(s)
        println("---")
        println("x25519 x: ${xPoint.affineXCoord.toBigInteger()}")
        println("x25519 y: ${xPoint.affineYCoord.toBigInteger()}")
    }

    @Test
    fun testCTRangeProof() {
        val maxValue = 1024 // range [0, 1023]
        val commitment = 30
        println("Commitment to the value of 30 in the range [0..$maxValue]")
        println("----")
        println("Curve25519 is used")
        val X25519 = CustomNamedCurves.getByName("Curve25519")
        val randomBase = X25519.curve.mapToPoint(X25519.g)
        println("Ensuring nothing up my sleeve... DONE" +
                "\n -- computing map2point of Curve25519.g.encoded using the Try-And-Increment method of Boneh et al... DONE" +
                "\n -- finding quadratic residue using Shanks-Tonelli algorithm... DONE")

        val cAmounts = CTx(X25519, randomBase)

        val x = BigInteger("4239784793298479823749828349823642394862349876923487")// private key
        println("Calculate a Pedersen blinding factor... DONE")
        val a = BigInteger.valueOf(commitment.toLong()) // committed to amount

        // xG + aH
        val pedersenPoint: ECPoint = cAmounts.pedersenCommitment(x, a)
        println("Compute Pedersen commitment to value $commitment... DONE")

        // [0..7] Range proof, all public keys as points.
        val rangeKeys: List<ECPoint> = cAmounts.createAllRingPublicKeyPoints(pedersenPoint, maxValue)

        assertEquals(X25519.g.multiply(x), rangeKeys[commitment])
        println("Compute all other public keys in ring (range keys) for commitments 0 to $maxValue... DONE")

        val h = cAmounts.aosComputehOpt(pedersenPoint)
        // val h = cAmounts.aosComputehOpt(rangeKeys)

        val y = cAmounts.aosComputey(x, h)

        val u = BigInteger("9093859079483274983264238467329443286328479432")
        println("Calculate u value of the AOS ring signature scheme... DONE")

        val chk1 = cAmounts.aosFirstChallengeOpt(y, pedersenPoint, u, h)
        // val chk1 = cAmounts.aosFirstChallenge(rangeKeys, y, pedersenPoint, u, h)

        val sis = kotlin.arrayOfNulls<BigInteger>(maxValue)
        var chk = chk1
        for (i in commitment + 1 until maxValue) {
            val si = BigInteger.valueOf(99999L * (i + 1)) // We should be able to recompute it.
            sis[i] = si
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk = cAmounts.aosOtherChallengesOpt(
                    // rangeKeys,
                    y,
                    pedersenPoint,
                    cAmounts.x9ECParameters.g.multiply(si).add(rangeKeys[i].multiply(chk)),
                    h.multiply(si).add(y.multiply(chk))
            )
        }

        val chk0 = chk
        for (i in 0 until commitment) {
            val si = BigInteger.valueOf(99999L * (i + 1)) // We should be able to recompute it.
            sis[i] = si
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk = cAmounts.aosOtherChallengesOpt(
                    // rangeKeys,
                    y,
                    pedersenPoint,
                    cAmounts.x9ECParameters.g.multiply(si).add(rangeKeys[i].multiply(chk)),
                    h.multiply(si).add(y.multiply(chk))
            )
        }

        val sk = cAmounts.aosSk(u, x, chk)
        sis[commitment] = sk
        val ctxSig = ctxSignature(chk0, sis.toList(), y)
        println("---")
        println("Calculating AOS signature... DONE")
        println("Signature: $ctxSig")

        // VERIFY
        var chk2 = ctxSig.chk0
        for (i in 0 until maxValue) {
            // chi+1 = H(L, y, m, [si] * g + [chi] * yi, [si] * h + [chi] * y)
            chk2 = cAmounts.aosOtherChallengesOpt(
                    // rangeKeys,
                    y,
                    pedersenPoint,
                    cAmounts.x9ECParameters.g.multiply(ctxSig.si[i]).add(rangeKeys[i].multiply(chk2)),
                    h.multiply(ctxSig.si[i]).add(y.multiply(chk2))
            )
            // println(chk2)
        }
        assertEquals(ctxSig.chk0, chk2)
        println("Verifying Range Proof... DONE")
    }

    @Test
    fun `Borromean signature test`() {
        val m = "messageToSign".toByteArray()
        val X25519 = NISTNamedCurves.getByName("P-256")
        val randomKeys = generateRandomKeys(X25519.g, 16, 4)

        val sig = Borromean.sign(X25519.n, X25519.g, m, randomKeys.publicKeys, randomKeys.indices, randomKeys.privateKeys)
        Borromean.verify(X25519.g, m, randomKeys.publicKeys, sig)
    }
}