package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.schemas.MappedSchema
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.*
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import java.util.*

class SanityCheckingValidatingNotaryServiceTests {

    private val notaryNodeFactory: (MockNodeArgs, CordappLoader?) -> InternalMockNetwork.MockNode = { args, _ ->
        InternalMockNetwork.MockNode(args, JarScanningCordappLoader.fromDirectories(emptyList()))
    }

    private val mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages(
            "net.corda.testing.contracts"),
            notaryNodeFactory = notaryNodeFactory)

    private val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
    private val notaryNode = mockNet.defaultNotaryNode
    private val notary = mockNet.defaultNotaryIdentity
    private val alice = aliceNode.info.singleIdentity()

    @Ignore
    @Test
    fun `should report missing class errors in a user-friendly manner`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice, notary)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now(), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    private fun runNotaryClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceNode.services.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    private fun issueState(serviceHub: ServiceHub, identity: Party, notary: Party = this.notary): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}