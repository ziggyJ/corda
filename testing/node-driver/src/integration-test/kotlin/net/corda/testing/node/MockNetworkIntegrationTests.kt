package net.corda.testing.node

import net.corda.testing.node.internal.ProcessUtilities.startJavaProcess
import org.junit.Test
import kotlin.test.assertEquals

class MockNetworkIntegrationTests {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MockNetwork(MockNetworkParameters()).run {
                repeat(2) { createNode() }
                runNetwork()
                stopNodes()
            }
        }
    }

    @Test
    fun `does not leak non-daemon threads`() {
        assertEquals(0, startJavaProcess<MockNetworkIntegrationTests>(emptyList()).waitFor())
    }
}
