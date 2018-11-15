package net.corda.core.node.services

import net.corda.core.DoNotImplement
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters

/**
 * Interface for handling network parameters storage used for resolving transactions according to parameters that were
 * historically in force in the network.
 */
@DoNotImplement //TODO check DeleteFromDJVM
interface NetworkParametersStorage {
    /**
     * Hash of the current parameters for the network.
     */
    val currentParametersHash: SecureHash

    /**
    * Hash of the current parameters for the network.
    */
    val currentParameters: NetworkParameters

    /**
     * For backwards compatibility, these parameters will be used for resolving historical transactions in the chain.
     */
    val defaultParametersHash: SecureHash

    /**
     * For backwards compatibility, these parameters will be used for resolving historical transactions in the chain.
     */
    val defaultParameters: NetworkParameters

    /**
     * Return network parameters for the given hash. Null if there are no parameters for this hash in the storage and we are unable to
     * get them from network map.
     */
    fun readParametersFromHash(hash: SecureHash): NetworkParameters?

    /**
     * Save signed network parameters data. Internally network parameters bytes should be stored with the signature.
     * It's because of ability of older nodes to function in network where parameters were extended with new fields.
     * Hash should always be calculated over the serialized bytes.
     */
    fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>)

    /** Returns true if and only if the given [Party] is a notary, which is defined by the network parameters. */
    fun isNotary(party: Party): Boolean

    /**
     * Returns true if and only if the given [Party] is validating notary. For every party that is a validating notary,
     * [isNotary] is also true.
     * @see isNotary
     */
    fun isValidatingNotary(party: Party): Boolean
}
