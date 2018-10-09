package net.corda.node.services.config

class ConfigurationFactory private constructor() {

    companion object {

        @JvmStatic
        fun builderSelectorFor(schema: Configuration.Schema): Configuration.Builder.Selector = Konfiguration.Builder.Selector(schema)

        @JvmStatic
        fun schemaOf(strict: Boolean, properties: Iterable<Configuration.Property<*>>): Configuration.Schema = Konfiguration.Schema(strict, properties)

        @JvmStatic
        fun propertyBuilder(): Configuration.Property.Builder = Konfiguration.Property.Builder()
    }
}