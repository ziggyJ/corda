package net.corda.dummy.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.dummy.basecontract.BaseDummyAssetContract
import net.corda.dummy.contract.DummyAssetContract

@StartableByRPC
class CreateDummyAssetFlow(val state: DummyAssetContract.State) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {
        val txb = TransactionBuilder()
                .addCommand(BaseDummyAssetContract.Commands.Issue())
                .addOutputState(state)
        val stx = serviceHub.signInitialTransaction(txb)
        stx.verify(serviceHub)
        return stx
    }
}