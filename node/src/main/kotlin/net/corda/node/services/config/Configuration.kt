package net.corda.node.services.config

import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.util.*

interface Configuration {

    companion object : ConfigurationBuilder {

        override val from: SourceSelector = TODO("not implemented")

        override fun build(): Configuration = TODO("not implemented")
    }

    fun mutable(): Configuration.Mutable = TODO("not implemented")

    operator fun <EXPECTED_VALUE> get(key: String): EXPECTED_VALUE

    // TODO sollecitom create a function that returns a mutable version of the configuration

    interface Mutable : Configuration {

        operator fun set(key: String, value: Any?)

        override fun mutable() = this
    }
}

interface SourceSelector {

    fun systemProperties(prefixFilter: String? = null): ConfigurationBuilder

    fun environment(): ConfigurationBuilder

    fun properties(properties: Properties): ConfigurationBuilder

    fun map(map: Map<String, Any?>): ConfigurationBuilder

    fun hierarchicalMap(map: Map<String, Any?>): ConfigurationBuilder

    val hocon: FormatAwareSourceSelector

    val yaml: FormatAwareSourceSelector

    val xml: FormatAwareSourceSelector

    val json: FormatAwareSourceSelector
}

interface FormatAwareSourceSelector {

    fun file(path: Path): ConfigurationBuilder

    fun resource(resourceName: String): ConfigurationBuilder

    fun reader(reader: Reader): ConfigurationBuilder

    fun inputStream(stream: InputStream): ConfigurationBuilder

    fun string(rawFormat: String): ConfigurationBuilder

    fun bytes(bytes: ByteArray): ConfigurationBuilder
}

// TODO sollecitom maybe join this with `Configuration` interface directly
interface ConfigurationBuilder {

    val from: SourceSelector

    fun build(): Configuration
}