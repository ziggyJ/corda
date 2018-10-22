package net.corda.sdk.tokens.states

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.sdk.tokens.LinearPointer
import net.corda.sdk.tokens.TokenDescription
import net.corda.sdk.tokens.TokenPointer

/**
 * Can include fungible or non-fungible [TokenTypeContract]s.
 * For now, all [amount]s are of [TokenPointer] to see how it works and performs.
 */
// TODO: Does it makes sense / is it possible to have nested TokenPointers? Think about this.
// How do we ensure that the reference data cannot get deleted?
// Can we have "create once and not update data?
open class Token<T : TokenDescription<*>>(
        override val amount: Amount<LinearPointer>,
        override val owner: AbstractParty
) : FungibleState<TokenPointer<T>>, OwnableState {
    /** The only participant should be the [owner]. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Issue(), Token(amount, newOwner))
    fun resolveTokenType(services: ServiceHub): TokenType<T>? = amount.token.resolve(services)?.state?.data

    interface Commands : CommandData
    class Issue : TypeOnlyCommandData(), Commands

}




