package net.corda.serialization.internal.model

import net.corda.serialization.internal.carpenter.*
import java.lang.reflect.Type

/**
 * A [TypeLoader] obtains local types whose [TypeIdentifier]s will reflect those of remote types.
 */
interface TypeLoader {
    /**
     * Obtains local types which will have the same [TypeIdentifier]s as the remote types.
     *
     * @param remoteTypeInformation The type information for the remote types.
     */
    fun load(remoteTypeInformation: Collection<RemoteTypeInformation>): Map<TypeIdentifier, Type>
}

/**
 * A [TypeLoader] that uses the [ClassCarpenter] to build a class matching the supplied [RemoteTypeInformation] if none
 * is visible from the current classloader.
 */
class ClassCarpentingTypeLoader(private val carpenter: RemoteTypeCarpenter, private val classLoader: ClassLoader): TypeLoader {

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    /**
     * Note: The result of this method might not contain some of the types included in the input, if they are not available (and not needed).
     *       This is done in order to maintain backwards-compatibility in as many cases as possible.
     *       More specifically, when an interface is not available, but all the classes that implement it are available in the classpath
     *       and thus do not need to be carpented, then this interface will not be loaded.
     */
    override fun load(remoteTypeInformation: Collection<RemoteTypeInformation>): Map<TypeIdentifier, Type> {
        val remoteInformationByIdentifier = remoteTypeInformation.associateBy { it.typeIdentifier }

        // Grab all the types we can from the cache, or the classloader.
        val noCarpentryRequired = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, _) ->
            try {
                identifier to cache.computeIfAbsent(identifier) { identifier.getLocalType(classLoader) }
            } catch (e: ClassNotFoundException) {
                null
            }
        }.toMap()

        // If we have everything we need, return immediately.
        if (noCarpentryRequired.size == remoteTypeInformation.size) return noCarpentryRequired

        // Identify the types which need carpenting up.
        // Includes only the interfaces of classes that require carpenting.
        val nonInterfacesRequiringCarpentry = remoteInformationByIdentifier.asSequence()
                .filter { (identifier, _) -> identifier !in noCarpentryRequired }
                .map { (_, information) -> information }
                .filter { it !is RemoteTypeInformation.AnInterface }.toSet()
        val interfacesRequiringCarpentry = nonInterfacesRequiringCarpentry.asSequence()
                .filter { it is RemoteTypeInformation.Composable }
                .flatMap { (it as RemoteTypeInformation.Composable).interfaces.asSequence() }
                .filter { it.typeIdentifier !in noCarpentryRequired.keys }.toSet()
        val requiringCarpentry = nonInterfacesRequiringCarpentry + interfacesRequiringCarpentry

        // Build the types requiring carpentry in reverse-dependency order.
        // Something else might be trying to carpent these types at the same time as us, so we always consult
        // (and populate) the cache.
        val carpented = CarpentryDependencyGraph.buildInReverseDependencyOrder(requiringCarpentry) { typeToCarpent ->
            cache.computeIfAbsent(typeToCarpent.typeIdentifier) {
                carpenter.carpent(typeToCarpent)
            }
        }

        // Return the complete map of types.
        return noCarpentryRequired + carpented
    }
}

