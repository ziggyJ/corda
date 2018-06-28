package net.corda.client.rpc

import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.RPCOps
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.User
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcTestUser
import net.corda.testing.node.internal.startInVmRpcClient
import net.corda.testing.node.internal.startRpcClient
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.junit.Rule

open class AbstractRPCTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    enum class RPCTestMode {
        InVm,
        Netty
    }

    var mode: RPCTestMode = RPCTestMode.valueOf(System.getProperty("rpcTestMode") ?: "InVm")

    data class TestProxy<out I : RPCOps>(
            val ops: I,
            val createSession: () -> ClientSession
    )

    inline fun <reified I : RPCOps> RPCDriverDSL.testProxy(
            ops: I,
            rpcUser: User = rpcTestUser,
            clientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
            serverConfiguration: RPCServerConfiguration = RPCServerConfiguration.DEFAULT
    ): TestProxy<I> {
        return when (mode) {
            RPCTestMode.InVm ->
                startInVmRpcServer(ops = ops, rpcUser = rpcUser, configuration = serverConfiguration).flatMap {
                    startInVmRpcClient<I>(rpcUser.username, rpcUser.password, clientConfiguration).map {
                        TestProxy(it, { startInVmArtemisSession(rpcUser.username, rpcUser.password) })
                    }
                }
            RPCTestMode.Netty ->
                startRpcServer(ops = ops, rpcUser = rpcUser, configuration = serverConfiguration).flatMap { (broker) ->
                    startRpcClient<I>(broker.hostAndPort!!, rpcUser.username, rpcUser.password, clientConfiguration).map {
                        TestProxy(it, { startArtemisSession(broker.hostAndPort!!, rpcUser.username, rpcUser.password) })
                    }
                }
        }.get()
    }
}
