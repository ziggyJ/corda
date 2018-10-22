package net.corda.services.vault

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testMessage.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.startNode
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import kotlin.test.assertEquals

class ParallelRpcVaultQueryForDifferentContractTypes {

    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    companion object {
        val lock1 = Object()
        val lock2 = Object()
        val latch = CountDownLatch(2)
    }

    @Test
    fun test() {
        val params = NodeParameters(rpcUsers = users)
        driver(DriverParameters(startNodesInProcess = false, extraCordappPackagesToScan =
        listOf("net.corda.finance.contracts", "net.corda.finance.schemas", MessageState::class.packageName, DummyState::class.packageName))) {
            val node = startNode(ALICE_NAME, devMode = true, parameters = params).getOrThrow()

            //setup test data
            CordaRPCClient(node.rpcAddress).use(user.username, user.password) {
                it.proxy.startFlow(::SaveMessageFlow, Message("Hello world!"), defaultNotaryIdentity).returnValue.getOrThrow()
                it.proxy.startFlow(::SaveDummyMessageFlow, DummyMessage(100), defaultNotaryIdentity).returnValue.getOrThrow()
            }

            val pool = Executors.newFixedThreadPool(2)

            val fut1 = pool.submit(QueryMessage(CordaRPCClient(node.rpcAddress), user.username, user.password))
            val fut2 = pool.submit(QueryDummyMessage(CordaRPCClient(node.rpcAddress), user.username, user.password))

            assertEquals("Hello world!", fut1.get())
            assertEquals(100, fut2.get())
        }
    }

    class QueryMessage(val client: CordaRPCClient, val user: String, val password: String) : Callable<String> {
        override fun call(): String {
            synchronized(lock1) {
                client.start(user, password).use { connection ->
                    val rpc = connection.proxy
                    latch.countDown()
                    val result: String = rpc.vaultQuery(MessageState::class.java).states[0].state.data.message.value
                    return result
                }
            }
        }
    }

    class QueryDummyMessage(val client: CordaRPCClient, val user: String, val password: String) : Callable<Int> {
        override fun call(): Int {
            synchronized(lock2) {
                client.start(user, password).use { connection ->
                    val rpc = connection.proxy
                    latch.countDown()
                    val result: Int = rpc.vaultQuery(DummyState::class.java).states[0].state.data.message.value
                    return result
                }
            }
        }
    }
}

@StartableByRPC
class SaveMessageFlow(private val message: Message, private val notary: Party) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(MessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, MESSAGE_CONTRACT_PROGRAM_ID), txCommand)
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(signedTx))
    }
}

@StartableByRPC
class SaveDummyMessageFlow(private val message: DummyMessage, private val notary: Party) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val messageState = DummyState(message = message, by = ourIdentity)
        val txCommand = Command(DummyContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, DUMMY_CONTRACT_PROGRAM_ID), txCommand)
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(signedTx))
    }
}


@CordaSerializable
data class DummyMessage(val value: Int)

data class DummyState(val message: DummyMessage, val by: Party, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override val participants: List<AbstractParty> = listOf(by)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is DummySchemaV1 -> DummySchemaV1.DummyMessage(
                    by = by.name.toString(),
                    value = message.value
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummySchemaV1)
}

object DummySchema
object DummySchemaV1 : MappedSchema(
        schemaFamily = DummySchema.javaClass,
        version = 1,
        mappedTypes = listOf(DummyMessage::class.java)) {

    @Entity
    @Table(name = "dummy_messages")
    class DummyMessage(
            @Column(name = "message_by", nullable = false)
            var by: String,

            @Column(name = "message_value", nullable = false)
            var value: Int
    ) : PersistentState()
}

const val DUMMY_CONTRACT_PROGRAM_ID = "net.corda.services.vault.DummyContract"

open class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            "No inputs should be consumed when sending a message." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<DummyState>().single()
            "Message sender must sign." using (command.signers.containsAll(out.participants.map { it.owningKey }))
            "Message value must not be empty." using (out.message.value >= 0)
        }
    }

    interface Commands : CommandData {
        class Send : Commands
    }
}
