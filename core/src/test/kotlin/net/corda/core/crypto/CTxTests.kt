package net.corda.core.crypto

import net.corda.core.crypto.zkp.*
import net.corda.core.crypto.zkp.BulletProofs.computeGenerators
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
        val maxValue = 256 // range [0, 256)
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
        val a = BigInteger.valueOf(commitment.toLong()) // commitment to an amount.

        // xG + aH
        val pedersenPoint: ECPoint = cAmounts.pedersenCommitment(x, a)
        println("Compute Pedersen commitment to value $commitment... DONE")

        // [0..7] Range proof, all public keys as points.
        val rangeKeys: List<ECPoint> = cAmounts.createAllRingPublicKeyPoints(pedersenPoint, maxValue)

        assertEquals(X25519.g.multiply(x), rangeKeys[commitment])
        println("Compute all other public keys in ring (range keys) for commitments [0-$maxValue)... DONE")

        val aosSig = AOS.sign(X25519.n, X25519.g, pedersenPoint.getEncoded(true), rangeKeys, a.toInt(), x)
        println("AOS Range Proof ... DONE")

        AOS.verify(X25519.g, pedersenPoint.getEncoded(true), rangeKeys, aosSig)
        println("Verifying Range Proof... DONE")
    }

    @Test
    fun `AOS signature test`() {
        val m = "messageToSign".toByteArray()
        val X25519 = NISTNamedCurves.getByName("P-256")
        // X25519.curve.decodePoint()
        val randomKeys = generateRandomAOSKeys(X25519.g, 256)

        val sig = AOS.sign(X25519.n, X25519.g, m, randomKeys.publicKeys, randomKeys.index, randomKeys.privateKey)
        AOS.verify(X25519.g, m, randomKeys.publicKeys, sig)
    }

    @Test
    fun `Borromean signature test`() {
        val m = "messageToSign".toByteArray()
        val X25519 = NISTNamedCurves.getByName("P-256")
        val randomKeys = generateRandomBorromeanKeys(X25519.g, 16, 4)

        val sig = Borromean.sign(X25519.n, X25519.g, m, randomKeys.publicKeys, randomKeys.indices, randomKeys.privateKeys)
        Borromean.verify(X25519.g, m, randomKeys.publicKeys, sig)
    }

    @Test
    fun binaryRespresentation() {
        val aL = BulletProofs.aL(BigInteger.valueOf(100), 8)
        aL.forEach { print(it) }
    }

    @Test
    fun `compute generators`() {
        val curve = NISTNamedCurves.getByName("P-256")
        val gens = computeGenerators(curve.curve, curve.g, 64, "Corda".toByteArray())

        println(gens)
    }
}