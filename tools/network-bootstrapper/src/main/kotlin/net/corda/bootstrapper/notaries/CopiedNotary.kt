package net.corda.bootstrapper.notaries

import com.typesafe.config.ConfigFactory
import net.corda.bootstrapper.nodes.CopiedNode
import net.corda.core.identity.CordaX500Name
import java.io.File

class CopiedNotary(configFile: File, baseDirectory: File,
                   copiedNodeConfig: File, copiedNodeDir: File, val nodeInfoFile: File) :
        CopiedNode(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir) {
    private val config = ConfigFactory.parseFile(configFile)
    val isClusteredNotary = config.hasPath("notary.serviceLegalName")
    fun notaryServiceId(): CordaX500Name {
                return CordaX500Name.parse(config.getString("notary.serviceLegalName"))
   }
}


fun CopiedNode.toNotary(nodeInfoFile: File): CopiedNotary {
    return CopiedNotary(this.configFile, this.baseDirectory, this.copiedNodeConfig, this.copiedNodeDir, nodeInfoFile)
}
