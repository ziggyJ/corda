package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
@KeepForDJVM
@CordaSerializable
data class PartyAndReference(val party: AbstractParty, val reference: OpaqueBytes) {
    override fun toString() = "$party$reference"
}