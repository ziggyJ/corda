package net.corda.node.services.config

import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.util.*

interface Configuration {

    operator fun <EXPECTED_VALUE> get(key: String): EXPECTED_VALUE

    companion object : ConfigurationBuilder {

        override val from: SourceSelector = TODO("not implemented")

        override fun build(): Configuration = TODO("not implemented")
    }
}

interface SourceSelector {

    fun systemProperties(): ConfigurationBuilder

    fun environment(): ConfigurationBuilder

    fun properties(properties: Properties): ConfigurationBuilder

    fun map(map: Map<String, Any?>): ConfigurationBuilder

    fun hierarchicalMap(map: Map<String, Any?>): ConfigurationBuilder

    val hocon: FileSourceSelector

    val yaml: FileSourceSelector

    val xml: FileSourceSelector

    val json: FileSourceSelector
}

interface FileSourceSelector {

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