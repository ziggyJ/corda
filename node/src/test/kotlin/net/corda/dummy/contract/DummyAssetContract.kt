package net.corda.dummy.contract

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.dummy.basecontract.BaseDummyAssetContract
import java.util.*

class DummyAssetContract : BaseDummyAssetContract() {

    override fun verify(tx: LedgerTransaction) {
    }

    data class State(
            override val amount: Amount<Issued<Currency>>,
            override val owner: AbstractParty
    ) : BaseDummyAssetContract.State(amount, owner){
        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)
        override val participants = listOf(owner)
    }
}

