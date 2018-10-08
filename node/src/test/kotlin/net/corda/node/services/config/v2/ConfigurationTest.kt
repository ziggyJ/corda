package net.corda.node.services.config.v2

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

// TODO sollecitom add more tests, including writing to various formats / media, separate the tests in terms of reading, writing and using.
// TODO sollecitom perhaps create a way of producing a configuration object from key pairs.
class ConfigurationTest {

    @Test
    fun loading_from_specific_file_works() {

        val overriddenMyLegalNameValue = "O=Bank B,L=London,C=GB"
        System.setProperty("corda.configuration.myLegalName", overriddenMyLegalNameValue)

        val configFilePath = Paths.get("/home/michele/Projects/corda-open-source/node/src/test/resources/net/corda/node/services/config/node.conf")

        val myLegalName = Configuration.Property.ofType.string("myLegalName")
        val schema = ConfigSchema.withProperties(myLegalName)

        val configuration = Configuration.withSchema(schema).from.hocon.file(configFilePath).from.systemProperties("corda.configuration").build()

        val myLegalNameValue = configuration[myLegalName]

        assertThat(myLegalNameValue).isEqualTo(overriddenMyLegalNameValue)
    }

    @Test
    fun programmatic_configuration_setup_works() {

        val myLegalNameValue = "O=Bank B,L=London,C=GB"

        val myLegalName = Configuration.Property.ofType.string("myLegalName")
        val schema = ConfigSchema.withProperties(myLegalName)

        val configuration = Configuration.withSchema(schema).with.value(myLegalName, myLegalNameValue).build()

        assertThat(configuration[myLegalName]).isEqualTo(myLegalNameValue)
    }

    @Test
    fun programmatic_configuration_builder_works() {

        val myLegalNameValue = "O=Bank B,L=London,C=GB"

        val myLegalName = Configuration.Property.ofType.string("myLegalName")
        val schema = ConfigSchema.withProperties(myLegalName)

        val configuration = Configuration.withSchema(schema).empty.apply {

            this[myLegalName] = myLegalNameValue
        }.build()

        assertThat(configuration[myLegalName]).isEqualTo(myLegalNameValue)
    }

    @Test
    fun nested_property_works() {

        val myLegalNameValue = "O=Bank B,L=London,C=GB"
        val portValue = 8080
        val addressValue = "localhost"

        val address = Configuration.Property.ofType.string("address")
        val port = Configuration.Property.ofType.int("port")
        val rpcSettingsSchema = ConfigSchema.withProperties(address, port)

        val myLegalName = Configuration.Property.ofType.string("myLegalName")
        val rpcSettings = Configuration.Property.ofType.nested("rpcSettings", rpcSettingsSchema)
        val schema = ConfigSchema.withProperties(myLegalName, rpcSettings)

        val configuration = Configuration.withSchema(schema).with.value(myLegalName, myLegalNameValue).with.value(port, portValue).with.value(address, addressValue).build()

        assertThat(configuration[myLegalName]).isEqualTo(myLegalNameValue)

        val retrievedRpcSettings = configuration[rpcSettings]
        assertThat(retrievedRpcSettings[address]).isEqualTo(addressValue)
        assertThat(retrievedRpcSettings[port]).isEqualTo(portValue)
    }
}