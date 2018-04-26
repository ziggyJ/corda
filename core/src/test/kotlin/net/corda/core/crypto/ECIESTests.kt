package net.corda.core.crypto

import net.corda.core.utilities.OpaqueBytes
import org.bouncycastle.crypto.BasicAgreement
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.IESEngine
import org.bouncycastle.crypto.generators.KDF2BytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.jcajce.provider.asymmetric.ec.IESCipher
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.spec.IESParameterSpec
import org.bouncycastle.pqc.jcajce.provider.util.CipherSpiExt
import org.bouncycastle.util.encoders.Hex
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class ECIESTests {

    @Test
    fun `Test ECIES`() {
        val MAC_KEY_SIZE_IN_BITS = 256
        val AES_KEY_SIZE_IN_BITS = 256
        val plaintext = "hello".toByteArray()

        // if we don't reuse the ECDH key, this can be null.
        val derivation = Hex.decode("202122232425262728292a2b2c2d2e2f")
        val encoding = Hex.decode("303132333435363738393a3b3c3d3e3f")

        /*
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
        val curveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
        keyPairGenerator.initialize(curveParameterSpec, SecureRandom())
        */

        // Try using Corda's default algorithms
        val KeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        // Receiver's public key.
        val remotePublicKey = KeyPair.public as ECPublicKey
        // Receiver's private key.
        val localPrivateKey = KeyPair.private as ECPrivateKey

        // init cipher.
        val engine = IESEngine(ECDHBasicAgreementWithKeyCheck(),
                KDF2BytesGenerator(SHA256Digest()),
                HMac(SHA256Digest()),
                PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine())))
        val cipher = IESCipher(engine)
        val nonce = Hex.decode("000102030405060708090a0b0c0d0e0f")
        val parameterSpec = IESParameterSpec(derivation, encoding, MAC_KEY_SIZE_IN_BITS, AES_KEY_SIZE_IN_BITS, nonce)

        // encrypt.
        cipher.engineInit(CipherSpiExt.ENCRYPT_MODE, remotePublicKey, parameterSpec, newSecureRandom())
        val ciphertext = cipher.engineDoFinal(plaintext, 0, plaintext.size)

        // decrypt.
        cipher.engineInit(CipherSpiExt.DECRYPT_MODE, localPrivateKey, parameterSpec, newSecureRandom())
        val decrypted = cipher.engineDoFinal(ciphertext, 0, ciphertext.size)

        assertEquals(OpaqueBytes(plaintext), OpaqueBytes(decrypted))
    }

    // Extend ECDHBasicAgreement so that key is validated.
    class ECDHBasicAgreementWithKeyCheck : ECDHBasicAgreement() {
        private var key: ECPrivateKeyParameters? = null

        override fun init(key: CipherParameters) {
            this.key = key as ECPrivateKeyParameters
        }

        override fun getFieldSize(): Int {
            return (key!!.parameters.curve.fieldSize + 7) / 8
        }

        override fun calculateAgreement(pubKey: CipherParameters): BigInteger {
            val pub = pubKey as ECPublicKeyParameters
            if (pub.parameters != key!!.parameters) {
                throw IllegalStateException("ECDH public key has wrong domain parameters")
            }
            // Adding extra key validation test, because it's missing from BasicAgreement.
            require(pub.q.isValid && !pub.q.isInfinity && pub.parameters == key!!.parameters)

            val P = pub.q.multiply(key!!.d).normalize()

            if (P.isInfinity) {
                throw IllegalStateException("Infinity is not a valid agreement value for ECDH")
            }

            return P.affineXCoord.toBigInteger()
        }
    }

}