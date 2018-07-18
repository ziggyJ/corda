package net.corda.core.crypto

import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.pqc.math.linearalgebra.IntegerFunctions
import java.math.BigInteger

fun ECCurve.mapToPoint(s: String): ECPoint {
    val k = 256 // Security parameter, based on Boneh et al. scheme.
    val q = this.field.characteristic // Fq
    val a = this.a.toBigInteger() // a,b from x^3 + ax + b
    val b = this.b.toBigInteger()
    val hashBytes = SecureHash.sha256(s).bytes

    var x = BigInteger(hashBytes).mod(q)
    for (i in 0..k) {
        try {
            // Check if x^3 + ax + b is a quadratic residue in Fq.
            // Compute the square root of a BigInteger modulo a prime employing the
            // Shanks-Tonelli algorithm.
            val sqrt = IntegerFunctions.ressol(x.pow(3) + a.multiply(x) + b, q)
            val ecPoint = this.createPoint(x, sqrt)
            require (ecPoint.isValid && !ecPoint.isInfinity) // Let's ensure the point is valid.
            return ecPoint
        } catch (e: Exception) {
            // Omit errors.
        }
        x++
    }
    // Highly unlikely (1 out of 2^256)
    throw IllegalArgumentException("No EC point can be created for $s")
}


class ConfidentialAmounts {
    val x9 = NISTNamedCurves.getByName("P-256") // or whatever curve you want to use
    val n: BigInteger = x9.n // ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
    val h: BigInteger = x9.h // co-factor
    val g: ECPoint = x9.g // base point G
    val o = x9.curve.order // this is n
    val q = x9.curve.field.characteristic // ffffffff00000001000000000000000000000000ffffffffffffffffffffffff
    val f = x9.curve.fieldSize // 256.
    val a = x9.curve.a.toBigInteger() // x^3 + ax + b
    val b = x9.curve.b.toBigInteger()
    val curve = x9.curve

    fun mapToPoint(s: String) {
        val hashBytes = SecureHash.sha256(s).bytes
        val hashNum = BigInteger(hashBytes)
        val hashModQ = hashNum.mod(q)
        println("hashNum $hashNum")
        println("hashModQ $hashModQ")

        var x = hashModQ
        for (i in 0..256) {
            try {
                val sqrt = IntegerFunctions.ressol(x.pow(3) + a.multiply(x) + b, q)
                println("FOUND $i $x , $sqrt")
                println("SQRT  $sqrt")
                println("SQRTx2 ${sqrt.pow(2).mod(q)}")
                // val xElement = curve.fromBigInteger(x)
                // val yElement = curve.fromBigInteger(sqrt)
                val ecPoint = curve.createPoint(x, sqrt)
                println(ecPoint.affineYCoord.toBigInteger())
                require (ecPoint.isValid && !ecPoint.isInfinity)
                break
            } catch (e: Exception) {
                println(e.message)
                println("not found")
            }
            x++
        }
    }

    fun eccOperations() {

       // val x9 = NISTNamedCurves.getByName("P-256") // or whatever curve you want to use
        mapToPoint("Kostas")
        println(n.toString(16))
        println(q.toString(16))

/*
       var x: BigInteger
       do {
           x = BigInteger(nBitLength, random)
       } while (x.equals(BigInteger.ZERO) || x.compareTo(n) >= 0)
       val randomPoint = g.multiply(x)*/
   }

}