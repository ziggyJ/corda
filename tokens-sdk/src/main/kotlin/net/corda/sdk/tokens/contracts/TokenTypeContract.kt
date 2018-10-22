package net.corda.sdk.tokens.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/** [TokenTypeContract]s can be concrete [Definition]s or [Pointer]s to to [Definition]. */
open class TokenTypeContract : Contract {

    companion object {
        val contractId = "net.corda.sdk.tokens.contracts.TokenTypeContract"
    }

    override fun verify(tx: LedgerTransaction) {
        TODO("not implemented")
    }
}