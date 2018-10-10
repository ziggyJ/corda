package net.corda.testing.node.internal

import net.corda.core.internal.div
import net.corda.testing.driver.TestCorDapp
import java.net.URL
import java.nio.file.Path

// TODO sollecitom move to the internal package at same level of TestCorDapp
internal class TestCorDappImpl internal constructor(override val name: String, override val version: String, override val vendor: String, override val title: String, override val classes: Set<Class<*>>, override val resources: Map<String, URL>, val willResourceBeAddedToCorDapp: (String, URL) -> Boolean) : TestCorDapp {

    internal val jarEntries: Set<JarEntryInfo> = TestCorDappPackager.jarEntries(classes, resources)

    override val allResourceUrls: Set<URL> = jarEntries.asSequence().map(JarEntryInfo::url).toSet()
}

internal class TestCorDappPackager {

    internal companion object {

        // TODO sollecitom move these
        private const val jarExtension = ".jar"
        private const val whitespace = " "
        private const val whitespaceReplacement = "_"

        // TODO sollecitom move
        private fun jarEntriesFromClasses(classes: Set<Class<*>>): Set<JarEntryInfo> {

            val illegal = classes.filter { it.protectionDomain?.codeSource?.location == null }
            if (illegal.isNotEmpty()) {
                throw IllegalArgumentException("Some classes do not have a location, typically because they are part of Java or Kotlin. Offending types were: ${illegal.joinToString(", ", "[", "]") { it.simpleName }}")
            }
            return classes.asSequence().map(Class<*>::jarEntryInfo).toSet()
        }

        private fun jarEntriesFromResources(resources: Map<String, URL>): Set<JarEntryInfo> {

            return resources.asSequence().map { (name, url) -> JarEntryInfo.ResourceJarEntryInfo(name, url) }.toSet()
        }

        internal fun packageAsJarWithPath(jarFilePath: Path, cordapp: TestCorDapp) {

            val jarEntries = (cordapp as? TestCorDappImpl)?.jarEntries ?: jarEntries(cordapp.classes, cordapp.resources)
            val willResourceBeAddedToCorDapp: (fullyQualifiedName: String, url: URL) -> Boolean = (cordapp as? TestCorDappImpl)?.willResourceBeAddedToCorDapp
                    ?: TestCorDapp.Builder.Companion::filterTestCorDappClass
            jarEntries.packageToCorDapp(jarFilePath, cordapp.name, cordapp.version, cordapp.vendor, cordapp.title, willResourceBeAddedToCorDapp)
        }

        internal fun packageAsJarInDirectory(parentDirectory: Path, cordapp: TestCorDapp): Path = (parentDirectory / cordapp.defaultJarName()).also { packageAsJarWithPath(it, cordapp) }

        internal fun jarEntries(classes: Set<Class<*>>, resources: Map<String, URL>): Set<JarEntryInfo> = jarEntriesFromClasses(classes) + jarEntriesFromResources(resources)

        private fun TestCorDapp.defaultJarName(): String = "${name}_$version$jarExtension".replace(whitespace, whitespaceReplacement)
    }
}