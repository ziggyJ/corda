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
        System.setProperty("corda.configuration.myLegalName", overriddenMyLegalNameValue.quoted())

        val configFilePath = Paths.get("/home/michele/Projects/corda-open-source/node/src/test/resources/net/corda/node/services/config/node.conf")

        val myLegalName = Configuration.Property.ofType.string("myLegalName")
        val schema = ConfigSchema.withProperties(myLegalName)

//        val configuration = Configuration.from().hocon.file(configFilePath).build(schema)
        val configuration = Configuration.withSchema(schema).from().hocon.file(configFilePath).from().systemProperties("corda.configuration").build()

        val myLegalNameValue = configuration[myLegalName]

        assertThat(myLegalNameValue).isEqualTo(overriddenMyLegalNameValue)
    }

    private fun String.quoted(): String = "\"$this\""
}