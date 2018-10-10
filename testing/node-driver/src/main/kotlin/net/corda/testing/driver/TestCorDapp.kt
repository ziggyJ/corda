package net.corda.testing.driver

import net.corda.core.DoNotImplement
import net.corda.testing.node.internal.TestCorDappImpl
import net.corda.testing.node.internal.classesForPackage
import net.corda.testing.node.internal.packageToJarPath
import java.io.File
import java.net.URL
import java.nio.file.Path

/**
 * Represents information about a CorDapp. Used to generate CorDapp JARs in tests.
 */
@DoNotImplement
interface TestCorDapp {

    // TODO sollecitom use a TestCorDapp.Info instead
    val name: String
    val title: String
    val version: String
    val vendor: String

    val classes: Set<Class<*>>

    val resources: Set<URL>

    fun packageAsJarInDirectory(parentDirectory: Path): Path

    fun packageAsJarWithPath(jarFilePath: Path)

    // TODO sollecitom make it work with defaults
    // TODO sollecitom maybe just TestCorDapp is enough
    class Builder(val name: String, val version: String, val vendor: String) {

        constructor(name: String, version: String) : this(name, version, "R3")

        private companion object {

            private val productionPathSegments = setOf<(String) -> String>({ "out${File.separator}production${File.separator}classes" }, { fullyQualifiedName -> "main${File.separator}${fullyQualifiedName.packageToJarPath()}" })
            private val excludedCordaPackages = setOf("net.corda.core", "net.corda.node")

            private fun filterTestCorDappClass(fullyQualifiedName: String, url: URL): Boolean {

                return isTestResource(fullyQualifiedName, url) || !isInExcludedCordaPackage(fullyQualifiedName)
            }

            private fun isTestResource(fullyQualifiedName: String, url: URL): Boolean {

                return productionPathSegments.asSequence().map { it.invoke(fullyQualifiedName) }.none { url.toString().contains(it) }
            }

            private fun isInExcludedCordaPackage(packageName: String): Boolean {

                return excludedCordaPackages.any { packageName.startsWith(it) }
            }
        }

        private val packages = mutableSetOf<String>()
        private val classes = mutableSetOf<Class<*>>()
        private val resources = mutableMapOf<String, URL>()

        private var resourceFilter: (fullyQualifiedName: String, url: URL) -> Boolean = ::filterTestCorDappClass

        fun plusPackages(firstPackage: String, vararg packages: String): TestCorDapp.Builder {

            return plusPackages(setOf(firstPackage, *packages))
        }

        fun plusPackages(packages: Iterable<String>): TestCorDapp.Builder {

            this.packages += packages
            return this
        }

        fun plusClasses(firstClass: Class<*>, vararg classes: Class<*>): TestCorDapp.Builder {

            return plusClasses(setOf(firstClass, *classes))
        }

        fun plusClasses(classes: Iterable<Class<*>>): TestCorDapp.Builder {

            this.classes += classes
            return this
        }

        fun plusResources(firstResource: Pair<String, URL>, vararg resources: Pair<String, URL>): TestCorDapp.Builder {

            return plusResources(setOf(firstResource, *resources))
        }

        fun plusResources(resources: Iterable<Pair<String, URL>>): TestCorDapp.Builder {

            this.resources += resources.toMap()
            return this
        }

        fun withResourceFilter(resourceFilter: (fullyQualifiedName: String, url: URL) -> Boolean): TestCorDapp.Builder {

            this.resourceFilter = resourceFilter
            return this
        }

        fun build(): TestCorDapp {

            val classesFromPackages = packages.flatMap(::classesForPackage).toSet()
            return TestCorDappImpl(name, version, vendor, name, classes + classesFromPackages, resources, resourceFilter)
        }
    }
}