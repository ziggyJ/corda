package net.corda.node.services.config

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.RequiredItem
import net.corda.core.internal.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

// TODO sollecitom add typed-property-based equivalent tests.
// TODO sollecitom add more tests, including writing to various formats / media, separate the tests in terms of reading, writing and using.
class ConfigurationTest {

//    @Test
//    fun loading_from_specific_file_works() {
//
//        val configFilePath = ConfigurationTest::class.java.getResource("node.conf").toPath()
//        val configuration = Configuration.from.hocon.file(configFilePath).build()
//
//        val myLegalName: String = configuration["myLegalName"]
//        val useSslForRpc: Boolean = configuration["rpcSettings.useSsl"]
//
//        assertThat(myLegalName).isEqualTo("O=Bank A,L=London,C=GB")
//        assertThat(useSslForRpc).isEqualTo(false)
//    }
//
//    @Test
//    fun loading_from_resource_works() {
//
//        val configuration = Configuration.from.hocon.resource("node.conf").build()
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
//        val configuration = Configuration.from.hocon.string(rawConfig).build()
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
//        val configuration = Configuration.from.systemProperties("corda.configuration").build()
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
//        val configuration = Configuration.from.systemProperties("corda.configuration").from.hocon.resource("node.conf").build()
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
//        val configuration1 = Configuration.from.systemProperties("corda.configuration").build()
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
//
//    // TODO sollecitom remove
//    @Test
//    fun blah() {
//
//        System.setProperty("corda.configuration.myLegalName", "test")
//
//        val config = Config().from.systemProperties()
//        config.addSpec(object : ConfigSpec("corda.configuration") {
//
//            val myLegalName by required<String>()
//        })
////        val config = Config { from.systemProperties().addSpec(object : ConfigSpec("corda.configuration") {}) }
//
//        val legalName = config.get<String>("corda.configuration.myLegalName")
//
//        assertThat(legalName).isEqualTo("test")
//    }
//
//    @Test
//    fun blah3() {
//
//        System.setProperty("corda.configuration.myLegalName", "test")
//
//        val config = Konfiguration.Builder().from.systemProperties().build()
//
//        val spec = object : ConfigSpec("corda.configuration") {
//
//            val myLegalName by required<String>()
//        }
//        config.value.addSpec(spec)
//
//        val legalName: String = config["corda.configuration.myLegalName"]
//        val legalName2: String = config.value.at("corda.configuration")["myLegalName"]
//
//        assertThat(legalName).isEqualTo("test")
//        assertThat(legalName2).isEqualTo("test")
//    }
//
//    // TODO sollecitom remove
//    @Test
//    fun blah2() {
//
//        System.setProperty("corda.configuration.myLegalName", "test")
//
//        val config = Config().from.systemProperties()
////        val config = Config { from.systemProperties().addSpec(object : ConfigSpec("corda.configuration") {}) }
//
//        val legalName = config.getRaw<String>("corda.configuration.myLegalName")
//
//        assertThat(legalName).isEqualTo("test")
//    }
//
//    private fun <VALUE> Config.getRaw(key: String): String? {
//
//        return sources.asSequence().map { source -> source.getOrNull(key) }.filter { value -> value?.isText() ?: false }.firstOrNull()?.toText()
//    }

    // TODO sollecitom remove
    @Test
    fun blah4() {

        System.setProperty("corda.configuration.myLegalName", "test")

        val spec = object : Configuration.Specification("corda.configuration") {

            val myLegalName by required<String>(description = "Legal name of the Corda node")
        }

        val config = Configuration.from.systemProperties().build(spec)

        val legalName: String = config["corda.configuration.myLegalName"]

        assertThat(legalName).isEqualTo("test")

        val legalName2 = config[spec.myLegalName]
        assertThat(legalName2).isEqualTo("test")
    }
}