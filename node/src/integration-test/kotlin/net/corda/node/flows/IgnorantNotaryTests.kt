package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import org.junit.Test
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.identity.AbstractParty
import net.corda.node.flows.IOUContract.Companion.IOU_CONTRACT_ID

data class IOUState(val value: Int,
                    val lender: Party,
                    val borrower: Party):
        ContractState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(lender, borrower)
}

class IOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID: ContractClassName = IOUContract::class.java.name
    }

    override fun verify(tx: LedgerTransaction) = requireThat {
        val output = tx.outputs.single().data
        "This must be an IOU transaction." using (output is IOUState)
        val iou = output as IOUState
        "I won't accept IOUs with a value over 100." using (iou.value <= 100)
    }

    interface Commands : CommandData {
        class Create : Commands
    }
}

@InitiatingFlow
@StartableByRPC
class Initiator(val iouValue: Int,
                val otherParty: Party) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Generate an unsigned transaction.
        val iouState = IOUState(iouValue, serviceHub.myInfo.legalIdentities.first(), otherParty)
        val txCommand = Command(IOUContract.Commands.Create(), iouState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(iouState, IOU_CONTRACT_ID)
                .addCommand(txCommand)

        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Send the state to the counterparty, and receive it back with their signature.
        val otherPartyFlow = initiateFlow(otherParty)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow)))

        // Notarise and record the transaction in both parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx))
    }
}

@InitiatedBy(Initiator::class)
class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
            override fun checkTransaction(stx: SignedTransaction) = Unit
        }

        return subFlow(signTransactionFlow)
    }
}

class IgnorantNotaryTests {

    @Test
    fun `validating notary without cordapp fails gracefully`() {
        val cordappForNodeA = TestCorDapp.Factory.create("nodeA_only", "1.0", classes = setOf(
                Initiator::class.java,
                IOUState::class.java,
                IOUContract::class.java))
        val cordappForNodeB = TestCorDapp.Factory.create("nodeB_only", "1.0", classes = setOf(
                Acceptor::class.java,
                IOUState::class.java,
                IOUContract::class.java))

        driver(DriverParameters(
                isDebug = true,
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, true)),
                cordappsForAllNodes = emptySet())) {

            val (nodeA, nodeB) = listOf(
                    startNode(additionalCordapps = setOf(cordappForNodeA)),
                    startNode(additionalCordapps = setOf(cordappForNodeB)))
                    .transpose().getOrThrow()

            try {
                nodeA.rpc.startFlow(::Initiator, 1, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            println(defaultNotaryNode.getOrThrow().baseDirectory)
            println(nodeA.baseDirectory)
        }
    }
}