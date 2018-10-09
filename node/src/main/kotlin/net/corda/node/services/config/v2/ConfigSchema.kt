package net.corda.node.services.config.v2

import net.corda.node.services.config.ConfigValidationError
import net.corda.node.services.config.Validator

// Additional benefits of going this way:
// - Allows to validate a raw configuration object.
// - Allows to display the structure of the configuration.
// TODO sollecitom rename and nest
interface ConfigSchema : Validator<Configuration, ConfigValidationError> {

    val prefix: String

    fun description(): String

    val properties: Set<Configuration.Property<*>>

    companion object {

        // TODO sollecitom maybe remove prefix from the publicly accessible constructors
        fun withProperties(prefix: String = "", strict: Boolean = false, properties: Iterable<Configuration.Property<*>>): ConfigSchema = ConfigPropertySchema(strict, prefix, properties)

        fun withProperties(vararg properties: Configuration.Property<*>, prefix: String = "", strict: Boolean = false): ConfigSchema = withProperties(prefix, strict, properties.toSet())

        fun withProperties(prefix: String = "", strict: Boolean = false, builder: Configuration.Property.Builder.() -> Iterable<Configuration.Property<*>>): ConfigSchema = withProperties(prefix, strict, builder.invoke(Konfiguration.Property.Builder()))
    }
}

private class ConfigPropertySchema(private val strict: Boolean, override val prefix: String, unorderedProperties: Iterable<Configuration.Property<*>>) : ConfigSchema {

    override val properties = unorderedProperties.sortedBy(Configuration.Property<*>::key).toSet()

    init {
        val invalid = properties.groupBy(Configuration.Property<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
        if (invalid.isNotEmpty()) {
            throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
        }
    }

    override fun validate(target: Configuration): Set<ConfigValidationError> {

        TODO("sollecitom")
//        val propertyErrors = properties.flatMap { property -> property.validate(target).map { error -> error.withContainingPath(property.contextualize(error.containingPath)) } }.toSet()
//        if (strict) {
//            val unknownKeys = target.root().keys - properties.map(Configuration.Property<*>::key)
//            return propertyErrors + unknownKeys.map(::unknownPropertyError)
//        }
//        return propertyErrors
    }

    private fun unknownPropertyError(key: String) = ConfigValidationError(key, message = "Unknown configuration key: \"$key\".")

    override fun description(): String {

        TODO("sollecitom")
//        val description = StringBuilder()
//        var rootDescription = configObject()
//        properties.forEach { property ->
//            rootDescription = rootDescription.withValue(property.key, ConfigValueFactory.fromAnyRef(typeRef(property)))
//        }
//        description.append(rootDescription.toConfig().serialize())
//
//        val nestedProperties = (properties + properties.flatMap(::nestedProperties)).filterIsInstance<NestedConfiguration.Property<*>>().distinctBy(NestedConfiguration.Property<*>::schema)
//        nestedProperties.forEach { property ->
//            description.append(System.lineSeparator())
//            description.append("${property.typeName}: ")
//            description.append(property.schema.description())
//            description.append(System.lineSeparator())
//        }
//        return description.toString()
    }

    private fun nestedProperties(property: Configuration.Property<*>): Set<Configuration.Property<*>> {

        TODO("sollecitom")
//        return when (property) {
//            is NestedConfiguration.Property<*> -> (property.schema as ConfigPropertySchema).properties
//            else -> emptySet()
//        }
    }

    private fun typeRef(property: Configuration.Property<*>): String {

        TODO("sollecitom")
//        return if (property is NestedConfiguration.Property<*>) {
//            "#${property.typeName}"
//        } else {
//            property.typeName
//        }
    }

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as ConfigPropertySchema

        if (properties != other.properties) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {

        return properties.hashCode()
    }
}