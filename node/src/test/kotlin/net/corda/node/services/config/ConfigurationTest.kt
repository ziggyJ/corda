package net.corda.node.services.config

import net.corda.core.internal.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

// TODO sollecitom add typed-property-based equivalent tests.
// TODO sollecitom add more tests, including writing to various formats / media, separate the tests in terms of reading, writing and using.
class ConfigurationTest {

    private object AddressesSpec : Configuration.Specification.Mutable() {

        val main by required<String>(description = "Externally visible address for RPC.")
        val admin by optional<String?>(default = null, description = "Admin address for RPC, mandatory when `useSsl` is set to `true`.")
    }

    private object RpcSettingsSpec : Configuration.Specification.Mutable() {

        val addresses by required<Configuration>(AddressesSpec, description = "Address configuration for RPC.")

        val useSsl by optional(default = false, description = "Whether to use SSL for RPC client-server communication")
    }

    private object NodeConfigSpec : Configuration.Specification.Mutable() {

        val myLegalName by required<String>(description = "Legal name of the identity of the Corda node")

        // TODO sollecitom rename the ones with config to start with `nested` or similar, otherwise is confusing.
        val rpcSettings by optional<Configuration>(RpcSettingsSpec, Configuration.empty(RpcSettingsSpec), description = "RPC settings")
    }

    @Test
    fun nested_configs() {

        val configFilePath = ConfigurationTest::class.java.getResource("node.conf").toPath()

        val configuration = Configuration.from.hocon.file(configFilePath).build(NodeConfigSpec)

        val rpcSettings: Configuration = configuration[NodeConfigSpec.rpcSettings]

        val useSslForRpc = rpcSettings[RpcSettingsSpec.useSsl]

        assertThat(useSslForRpc).isEqualTo(false)

        val addresses: Configuration = rpcSettings[RpcSettingsSpec.addresses]

        assertThat(addresses[AddressesSpec.main]).isEqualTo("my-corda-node:10003")
        assertThat(addresses[AddressesSpec.admin]).isEqualTo("my-corda-node:10004")
    }

//    @Test
//    fun loading_from_specific_file_works() {
//
//        val configFilePath = ConfigurationTest::class.java.getResource("node.conf").toPath()
//
//        val configuration = Configuration.from.hocon.file(configFilePath).build(nodeConfigSpec)
//
//        val properties = nodeConfigSpec.properties
//
//        val myLegalName = configuration[nodeConfigSpec.myLegalName]
//        val useSslForRpc: Boolean = configuration[nodeConfigSpec.RpcSettingsSpec.useSsl]
//
//        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//
//        val rpcSettings: Configuration = configuration[nodeConfigSpec.rpcSettings]
//
//        val useSslForRpc2 = rpcSettings[nodeConfigSpec.RpcSettingsSpec.useSsl]
//
//        assertThat(useSslForRpc2).isEqualTo(false)
//    }

//    @Test
//    fun loading_from_resource_works() {
//
//        val configuration = Configuration.from.hocon.resource("node.conf").build(nodeConfigSpec)
//
//        val myLegalName: String = configuration["myLegalName"]
//        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
//
//        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//    }
//
//    @Test
//    fun loading_from_string_works() {
//
//        val rawConfig = "\"myLegalName\" = \"O=Bank A,L=London,C=GB\"\n\"rpcSettings\" = {\n\"useSsl\" = false\n}"
//        val configuration = Configuration.from.hocon.string(rawConfig).build(nodeConfigSpec)
//
//        val myLegalName: String = configuration["myLegalName"]
//        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
//
//        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//    }
//
//    @Test
//    fun loading_from_system_properties_works() {
//
//        System.setProperty("corda.configuration.myLegalName", "O=Bank A,L=London,C=GB")
//        System.setProperty("corda.configuration.rpcSettings.useSsl", "false")
//
//        val configuration = Configuration.from.systemProperties("corda.configuration").build(nodeConfigSpec)
//
//        val myLegalName: String = configuration["myLegalName"]
//        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
//
//        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//    }
//
//    @Test
//    fun cascade_order_is_respected() {
//
//        System.setProperty("corda.configuration.myLegalName", "O=Bank B,L=London,C=GB")
//
//        val configuration = Configuration.from.systemProperties("corda.configuration").from.hocon.resource("node.conf").build(nodeConfigSpec)
//
//        val myLegalName: String = configuration["myLegalName"]
//        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
//
//        assertThat(myLegalName).isEqualTo("O=Bank B,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//    }
//
//    @Test
//    fun mutable_configuration_contract() {
//
//        val legalName1 = "O=Bank A,L=London,C=GB"
//        System.setProperty("corda.configuration.myLegalName", legalName1)
//
//        val configuration1 = Configuration.from.systemProperties("corda.configuration").build(nodeConfigSpec)
//
//        val legalName2 = "O=Bank B,L=London,C=GB"
//        val configuration2 = configuration1.mutable()
//
//        val myLegalNameRetrieved1: String = configuration1["myLegalName"]
//        assertThat(myLegalNameRetrieved1).isEqualTo(legalName1)
//
//        val myLegalNameRetrieved2: String = configuration2["myLegalName"]
//        assertThat(myLegalNameRetrieved2).isEqualTo(legalName1)
//
//        configuration2["myLegalName"] = legalName2
//
//        val myLegalNameRetrieved3: String = configuration2["myLegalName"]
//        assertThat(myLegalNameRetrieved3).isEqualTo(legalName2)
//
//        val myLegalNameRetrieved4: String = configuration1["myLegalName"]
//        assertThat(myLegalNameRetrieved4).isEqualTo(legalName1)
//    }

    // TODO sollecitom remove
    @Test
    fun blah4() {

        System.setProperty("corda.configuration.myLegalName", "test")

        val spec = object : Configuration.Specification.Mutable("corda.configuration") {

            val myLegalName by required<String>(description = "Legal name of the Corda node")
        }

        val config = Configuration.from.systemProperties().build(spec)

        assertThat(spec.properties).contains(spec.myLegalName)
        assertThat(spec.myLegalName.path).isEqualTo(listOf("myLegalName"))
        assertThat(spec.myLegalName.fullPath).isEqualTo(listOf("corda", "configuration", "myLegalName"))

        val legalName = config[spec.myLegalName]

        assertThat(legalName).isEqualTo("test")
    }
}