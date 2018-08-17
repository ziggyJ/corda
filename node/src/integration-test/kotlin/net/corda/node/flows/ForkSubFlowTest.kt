package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.services.Permissions
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@CordaSerializable
private data class ForkedSubFlowResult(val result: Any?)

@InitiatingFlow
class ForkingSubFlow<A>(private val subFlowToFork: FlowLogic<A>) : FlowLogic<A>() {
    @Suspendable
    override fun call(): A {
        val session = initiateFlow(ourIdentity)
        session.send(subFlowToFork)
        val result = session.receive<ForkedSubFlowResult>()
        return result.unwrap {
            @Suppress("UNCHECKED_CAST")
            it.result as A
        }
    }
}

@InitiatedBy(ForkingSubFlow::class)
class ForkedSubFlow(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        require(session.counterparty == ourIdentity)
        val subFlowToCall = session.receive<FlowLogic<*>>().unwrap { it }
        val result = subFlow(subFlowToCall)
        session.send(ForkedSubFlowResult(result))
    }
}

@Suspendable
fun <A> FlowLogic<*>.forkSubFlow(subFlow: FlowLogic<A>): A {
    return subFlow(ForkingSubFlow(subFlow))
}

/**
 * A very efficient summing flow. Seriously, this is super efficient.
 */
@StartableByRPC
@CordaSerializable
class ForkingSummingFlow(val numbers: List<Int>) : FlowLogic<Int>() {
    @Suspendable
    override fun call(): Int {
        return when {
            numbers.isEmpty() -> 0
            numbers.size == 1 -> numbers.first()
            else -> {
                val left = numbers.take(numbers.size / 2)
                val right = numbers.drop(numbers.size / 2)
                logger.info("Forking $left")
                val leftResult = forkSubFlow(ForkingSummingFlow(left))
                logger.info("Forking $right")
                val rightResult = forkSubFlow(ForkingSummingFlow(right))
                leftResult + rightResult
            }
        }
    }
}

@StartableByService
@StartableByRPC
@CordaSerializable
class CashIssueSubFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        println("Subflow calling CashIssueFlow()")
        subFlow(CashIssueFlow(100.DOLLARS, OpaqueBytes.of(1), serviceHub.networkMapCache.notaryIdentities.first()))
        println("Subflow completed CashIssueFlow()")
    }
}

@StartableByService
@StartableByRPC
@CordaSerializable
class ForkCashIssueFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        println("ForkSubflow calling CashIssueFlow()")
        forkSubFlow(CashIssueFlow(100.DOLLARS, OpaqueBytes.of(1), serviceHub.networkMapCache.notaryIdentities.first()))
        println("ForkSubflow completed CashIssueFlow()")
    }
}

@CordaService
class TestObserverService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    init {
        monitor()
    }

    private fun monitor() =
            appServiceHub.vaultService.trackBy<Cash.State>().updates.subscribe {
                it.produced.forEach {
                    println("trackBy() observer: Starting flow: $it")
                    // DEADLOCK workaround
                    val asd = Executors.newSingleThreadExecutor()
                    asd.execute {
                        appServiceHub.startFlow(CashIssueSubFlow())
//                        appServiceHub.startFlow(ForkCashIssueFlow())
                    }
//                    appServiceHub.startFlow(CashIssueSubFlow())
//                    appServiceHub.startFlow(ForkCashIssueFlow())
                    println("trackBy() observer: Completed flow: $it")
                }
            }
}

class ForkSubFlowTest {
    @Test
    fun `Test flow can be triggered by trackBy Observer`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance","net.corda.finance.flows"))) {
            val node = startNode(rpcUsers = listOf(User("a", "a", setOf(Permissions.all())))).getOrThrow()
            CordaRPCClient(node.rpcAddress).use("a", "a") {
                println("Test Starting flow")
//                it.proxy.startFlow(::CashIssueSubFlow).returnValue.getOrThrow()
                it.proxy.startFlow(::ForkCashIssueFlow).returnValue.getOrThrow()
                println("Test Completed flow")

                // note: triggers TestObserverService via its pre-registered trackBy() observer, which starts another Flow
                // DEADLOCKED here !!!
                val result = node.rpc.vaultQueryBy<Cash.State>()
                Assertions.assertThat(result.states.size).isGreaterThanOrEqualTo(2)

                val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum() }
                val sumCriteria = QueryCriteria.VaultCustomQueryCriteria(sum)
                val resultSum = node.rpc.vaultQueryBy<Cash.State>(sumCriteria)
                val totalCash = resultSum.otherResults[0] as Long / 100
                println("Sum of Cash states: $totalCash")
                Assertions.assertThat(totalCash).isGreaterThanOrEqualTo(200L)

                val moreResults = node.rpc.vaultQueryBy<Cash.State>()
                val totalCashStates = moreResults.states.size
                println("Count of Cash states: $totalCashStates")
                Assertions.assertThat(totalCashStates).isGreaterThanOrEqualTo(2)
            }
        }
    }

    @Test
    fun canForkSubFlows() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val node = startNode(rpcUsers = listOf(User("a", "a", setOf(Permissions.all())))).getOrThrow()
            CordaRPCClient(node.rpcAddress).use("a", "a") {
                it.proxy.waitUntilNetworkReady().getOrThrow()
                val result = it.proxy.startFlow(::ForkingSummingFlow, (1 .. 10).toList()).returnValue.getOrThrow()
                assertEquals(55, result)
            }
        }
    }
}