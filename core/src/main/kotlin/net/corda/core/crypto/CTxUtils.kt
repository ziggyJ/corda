package net.corda.core.crypto

import net.corda.core.utilities.toBase64
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.pqc.math.linearalgebra.IntegerFunctions
import java.math.BigInteger

private val k = 256  // Security parameter, based on Boneh et al. scheme.

fun ECCurve.mapToPoint(bytes: ByteArray): ECPoint {
    val q = this.field.characteristic // Fq
    val a = this.a.toBigInteger() // a,b from x^3 + ax + b
    val b = this.b.toBigInteger()
    val hashBytes = bytes.sha256().bytes

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
    // Highly unlikely to throw (normally 1 out of 2^k).
    throw IllegalArgumentException("No EC point can be created for ${bytes.toBase64()}")
}

fun ECCurve.mapToPoint(ecPoint: ECPoint): ECPoint {
    return this.mapToPoint(ecPoint.getEncoded(true))
}