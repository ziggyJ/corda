package net.corda.core.crypto.zkp

import net.corda.core.crypto.sha256
import net.corda.core.utilities.toBase64
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.pqc.math.linearalgebra.IntegerFunctions
import java.math.BigInteger

private val k = 256  // Security parameter, based on Boneh et al. scheme.

/** Map a ByteArray to an elliptic curve point using using the Boneh et al. Try-And-Increment algorithm. */
fun ECCurve.mapToPoint(bytes: ByteArray): ECPoint {
    val q = this.field.characteristic // Fq
    val a = this.a.toBigInteger() // a,b from x^3 + ax + b
    val b = this.b.toBigInteger()
    val hashBytes = bytes.sha256().bytes

    var x = BigInteger(hashBytes).mod(q)
    for (i in 0..k) {
        try {
            // Check if x^3 + ax + b is a quadratic residue in Fq.
            // Compute the square root of a BigInteger modulo a prime q.
            // Most elliptic curves (about 50%) are congruent to 3 mod 4, and in this case, the problem can be simplified by just
            // computing n(q+1)/4 (and check that that value squared gives you n - in our case y^2, so x^3 + ax + b).
            // If not q ≡ 3 mod 4, then we employ the Shanks-Tonelli algorithm.
            val sqrt = IntegerFunctions.ressol(x.pow(3) + a.multiply(x) + b, q)
            val ecPoint = this.createPoint(x, sqrt)
            require (ecPoint.isValid && !ecPoint.isInfinity) // Let's ensure the point is valid.
            return ecPoint
        } catch (e: Exception) {
            // Omit errors, as this is normal with this algorithm to increment value if we can't find a square root.
        }
        x++
    }
    // Highly unlikely to throw (normally 1 out of 2^k) after k retries.
    throw IllegalArgumentException("No EC point can be created for ${bytes.toBase64()}")
}

/** Helper function to map one [ECPoint] to another using mapToPoint, so after mapping their dlog relationship is unknown. */
fun ECCurve.mapToPoint(ecPoint: ECPoint): ECPoint {
    return this.mapToPoint(ecPoint.getEncoded(true))
}