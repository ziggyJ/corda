package net.corda.sdk.tokens

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

// TODO: Should only be able to compose pointers.

/** Base interface for defining potentially composable and wrappable token descriptions. */
@CordaSerializable
interface TokenDescription<T : Any> {
    val of: T
}

/** Implement this interface if the [TokenTypeContract] is intended to be fungible. */
interface FungibleTokenDescription<T : Any> : TokenDescription<T>, TokenizableAssetInfo {
    val symbol: String
    val name: String
}

/** Interface for tokens which are issuable. */
@CordaSerializable
interface Issuable {
    val issuer: Party
}

/** Interface for tokens which are redeemable. */
@CordaSerializable
interface Redeemable {
    val exitKeys: Collection<PublicKey>
}