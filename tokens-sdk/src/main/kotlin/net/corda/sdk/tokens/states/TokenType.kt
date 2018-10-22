package net.corda.sdk.tokens.states

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.sdk.tokens.TokenDescription
import net.corda.sdk.tokens.TokenPointer

/**
 * A [TokenType.Pointer] can be resolved to a [TokenType.Definition].
 *
 * @param T the type of token which is embedded within a [TokenDescription].
 */
data class TokenType<T : TokenDescription<*>>(
        val data: T,
        val creator: Party,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(creator)
    fun toPointer() = TokenPointer(linearId, data::class.java)

    interface TokenTypeCommands : CommandData

    class Create : TokenTypeCommands, TypeOnlyCommandData()
    class Update : TokenTypeCommands, TypeOnlyCommandData()
}
