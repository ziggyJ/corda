package net.corda.sdk.tokens.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class TokenContract : Contract {

    override fun verify(tx: LedgerTransaction) = Unit

    companion object {
        val contractId = "net.corda.sdk.tokens.contracts.TokenContract"
    }
}