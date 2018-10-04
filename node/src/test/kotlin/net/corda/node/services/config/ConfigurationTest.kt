package net.corda.node.services.config

import net.corda.core.internal.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

// TODO sollecitom add more tests, including writing to various formats / media, separate the tests in terms of reading, writing and using.
class ConfigurationTest {

    @Test
    fun loading_from_specific_file_works() {

        val configFilePath = ConfigurationTest::class.java.getResource("node.conf").toPath()
        val configuration = Configuration.from.hocon.file(configFilePath).build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]

        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }

    @Test
    fun loading_from_resource_works() {

        val configuration = Configuration.from.hocon.resource("node.conf").build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]

        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }

    @Test
    fun loading_from_string_works() {

        val rawConfig = "\"myLegalName\" = \"O=Bank A,L=London,C=GB\"\n\"rpcSettings\" = {\n\"useSsl\" = false\n}"
        val configuration = Configuration.from.hocon.string(rawConfig).build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]

        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }

    @Test
    fun loading_from_system_properties_works() {

        System.setProperty("corda.configuration.myLegalName", "O=Bank A,L=London,C=GB")
        System.setProperty("corda.configuration.rpcSettings.useSsl", "false")

        val configuration = Configuration.from.systemProperties("corda.configuration").build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]

        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }

    @Test
    fun cascade_order_is_respected() {

        System.setProperty("corda.configuration.myLegalName", "O=Bank B,L=London,C=GB")

        val configuration = Configuration.from.systemProperties("corda.configuration").from.hocon.resource("node.conf").build()

        val myLegalName: String = configuration["myLegalName"]
        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]

        assertThat(myLegalName).isEqualTo("O=Bank B,L=London,C=GB")
        assertThat(useSslForRpc).isEqualTo(false)
    }

    @Test
    fun mutable_configuration_contract() {

        val legalName1 = "O=Bank A,L=London,C=GB"
        System.setProperty("corda.configuration.myLegalName", legalName1)

        val configuration1 = Configuration.from.systemProperties("corda.configuration").build()

        val legalName2 = "O=Bank B,L=London,C=GB"
        val configuration2 = configuration1.mutable()

        val myLegalNameRetrieved1: String = configuration1["myLegalName"]
        assertThat(myLegalNameRetrieved1).isEqualTo(legalName1)

        val myLegalNameRetrieved2: String = configuration2["myLegalName"]
        assertThat(myLegalNameRetrieved2).isEqualTo(legalName1)

        configuration2["myLegalName"] = legalName2

        val myLegalNameRetrieved3: String = configuration2["myLegalName"]
        assertThat(myLegalNameRetrieved3).isEqualTo(legalName2)

        val myLegalNameRetrieved4: String = configuration1["myLegalName"]
        assertThat(myLegalNameRetrieved4).isEqualTo(legalName1)
    }
}