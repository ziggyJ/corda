package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.trackBy
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.cordapp.DummyRPCFlow
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@StartableByService
class DummyServiceFlow : FlowLogic<InvocationContext>() {
    companion object {
        object TEST_STEP : ProgressTracker.Step("Custom progress step")
    }
    override val progressTracker: ProgressTracker = ProgressTracker(TEST_STEP)

    @Suspendable
    override fun call(): InvocationContext {
        // We call a subFlow, otehrwise there is no chance to subscribe to the ProgressTracker
        subFlow(CashIssueFlow(100.DOLLARS, OpaqueBytes.of(1), serviceHub.networkMapCache.notaryIdentities.first()))
        progressTracker.currentStep = TEST_STEP
        return stateMachine.context
    }
}

@CordaService
class TestCordaService(val appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    fun startServiceFlow() {
        val handle = appServiceHub.startFlow(DummyServiceFlow())
        val context = handle.returnValue.get()
        assertEquals(this.javaClass.name, (context.origin as InvocationOrigin.Service).serviceClassName)
    }

    fun startServiceFlowAndTrack() {
        val handle = appServiceHub.startTrackedFlow(DummyServiceFlow())
        val count = AtomicInteger(0)
        val subscriber = handle.progress.subscribe { count.incrementAndGet() }
        handle.returnValue.get()
        // Simply prove some progress was made.
        // The actual number is currently 11, but don't want to hard code an implementation detail.
        assertTrue(count.get() > 1)
        subscriber.unsubscribe()
    }

}

@CordaService
class TestCordaService2(val appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    fun startInvalidRPCFlow() {
        val handle = appServiceHub.startFlow(DummyRPCFlow())
        handle.returnValue.get()
    }

}

@CordaService
class LegacyCordaService(@Suppress("UNUSED_PARAMETER") simpleServiceHub: ServiceHub) : SingletonSerializeAsToken()


@CordaService
class TestObserverService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
    init {
        monitor()
    }

    private fun monitor() =
            appServiceHub.vaultService.trackBy<Cash.State>().updates.subscribe {
                it.produced.forEach {
                    println("Starting flow for vault update: $it")
                    val handle = appServiceHub.startFlow(DummyServiceFlow())
                    handle.returnValue.get()
                    println("Completed flow for vault update: $it")
                }
            }
}

class CordaServiceTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var vaultFiller: VaultFiller

    @Before
    fun start() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.node.internal","net.corda.testing.internal.vault","net.corda.finance"))
        nodeA = mockNet.createNode()
        mockNet.startNodes()

        val dummyNotary = TestIdentity(mockNet.defaultNotaryIdentity.name, 20)
        vaultFiller = VaultFiller(nodeA.services, dummyNotary)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Can find distinct services on node`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        val service2 = nodeA.services.cordaService(TestCordaService2::class.java)
        val legacyService = nodeA.services.cordaService(LegacyCordaService::class.java)
        val observerService = nodeA.services.cordaService(TestObserverService::class.java)
        assertEquals(TestCordaService::class.java, service.javaClass)
        assertEquals(TestCordaService2::class.java, service2.javaClass)
        assertNotEquals(service.appServiceHub, service2.appServiceHub) // Each gets a customised AppServiceHub
        assertEquals(LegacyCordaService::class.java, legacyService.javaClass)
        assertEquals(TestObserverService::class.java, observerService.javaClass)
    }

    @Test
    fun `Can start StartableByService flows`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        service.startServiceFlow()
    }

    @Test
    fun `Can't start StartableByRPC flows`() {
        val service = nodeA.services.cordaService(TestCordaService2::class.java)
        assertFailsWith<IllegalArgumentException> { service.startInvalidRPCFlow() }
    }


    @Test
    fun `Test flow with progress tracking`() {
        val service = nodeA.services.cordaService(TestCordaService::class.java)
        service.startServiceFlowAndTrack()
    }

    @Test
    fun `Test flow can be triggered by trackBy Observer`() {
        nodeA.services.cordaService(TestObserverService::class.java)
        nodeA.startFlow(DummyServiceFlow())
        // note: triggers TestObserverService via its pre-registered trackBy() observer, which starts another Flow
        // DEADLOCKED here !!!
        val result = nodeA.services.vaultService.queryBy<Cash.State>()
        Assertions.assertThat(result.states).hasSize(1)
    }
}