package net.corda.networkDriver

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import picocli.CommandLine
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

fun main(args: Array<String>) {

    val parameters = Parameters()
    val commandline = CommandLine(parameters)
    commandline.parse(*args)

    if (commandline.isUsageHelpRequested) {
        CommandLine.usage(Parameters(), System.out)
        return
    }

    val seedNode = NetworkHostAndPort.parse(parameters.hostPort)

    val rpcAddresses = addresses(seedNode, parameters.username, parameters.password, parameters.filterPattern, parameters.includeNotaries).map {
        try {
            CordaRPCClient(it).start(parameters.username, parameters.password)
            it
        } catch (e: Exception) {
            println(e)
            println("ignoring $it")
            null
        }
    }.filterNotNull()

    // Connecting to version below 3.3 not supported.
    val rpcClients = rpcAddresses.map { it to CordaRPCClient(it).start(parameters.username, parameters.password) }.toMap()

    val rng = Random(23)

    Timer().schedule(0, 5000) {
        val clientAddr = rpcAddresses.shuffled(rng).first()
        val rpcClient = rpcClients[clientAddr]!!.proxy
        val myIdentity = rpcClient.nodeInfo().legalIdentities.first()
        val notaries = rpcClient.notaryIdentities()

        val peer = rpcClient.networkMapSnapshot().filterNot {
            it.legalIdentities.contains(myIdentity)
        }.filter {
            parameters.includeNotaries || !notaries.any { n -> it.legalIdentities.contains(n) }
        }.filter {
            parameters.filterPattern.isEmpty() || it.legalIdentities.map { it.name.toString() }.any { it.contains(parameters.filterPattern) }
        }.shuffled(rng).first().legalIdentities.first()

        println("all notaries: $notaries")
        val notary = notaries.first()
        println("$myIdentity -> $peer (notary $notary)")
        try {
            val response = rpcClient.startFlow(::CashIssueAndPaymentFlow, 100.POUNDS, OpaqueBytes.of(1), peer, false, notary).returnValue.get(30, TimeUnit.SECONDS)
            println("response: $response")
        } catch(e: Exception) {
            println(e)
        }
    }
}

fun addresses(seedNode: NetworkHostAndPort, userName: String, password: String, filterPattern: String, includeNotaries: Boolean): List<NetworkHostAndPort> {
    val client = CordaRPCClient(seedNode).start(userName, password)
    val notaries = client.proxy.notaryIdentities()
    val networkMap = client.proxy.networkMapSnapshot()


    for (entry in networkMap) {
        println("${entry.legalIdentities}: ${entry.addresses}")
    }
    val netMapFiltered = networkMap.filter {
        includeNotaries || !notaries.any { n -> it.legalIdentities.contains(n) }
    }.filter {
        filterPattern.isEmpty() || it.legalIdentities.map { it.name.toString() }.any { it.contains(filterPattern) }
    }

    // RPC port is P2P port plus one by convention.
    val rpcAddresses = netMapFiltered.map { it.addresses[0] }.map { it.copy(port = it.port+1) }

    client.close()
    println("discovered RPC addresses: $rpcAddresses")

    return rpcAddresses
}
