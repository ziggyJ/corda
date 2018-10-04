package net.corda.node.services.config

import net.corda.core.internal.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ConfigurationTest {

    // val configuration = Configuration.from.hocon.file(configFilePath).from.hocon.resource("node.conf").from.systemProperties().from.environment().build()

    @Test
    fun loading_from_specific_file_works() {

        val configFilePath = ConfigurationTest::class.java.getResource("node.conf").toPath()

        val configuration = Configuration.from.hocon.file(configFilePath).build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }
}