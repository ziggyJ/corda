package net.corda.node.internal

import net.corda.dummy.contract.DummyAssetContract
import net.corda.dummy.flows.CreateDummyAssetFlow
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Test

class ScanContractExtendingAbstractContractTest {
    private val mockNet = MockNetwork(cordappPackages = listOf("net.corda.dummy.contract", "net.corda.dummy.flows"), threadPerNode = true)

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `restart with no network map cache update`() {
        val alice = mockNet.createNode(legalName = ALICE_NAME)
        val bob = mockNet.createNode(legalName = BOB_NAME)
        val aliceId = alice.info.legalIdentities.first()

        val dummyAsset = DummyAssetContract.State(100.POUNDS.`issued by`(aliceId.ref(1)), aliceId)
        val tx = alice.startFlow(CreateDummyAssetFlow(dummyAsset))
        println(tx)
    }
}
