package net.corda.testing.node.internal

import net.corda.core.internal.div
import net.corda.testing.driver.TestCorDapp
import java.net.URL
import java.nio.file.Path

// TODO sollecitom move to the internal package at same level of TestCorDapp
internal class TestCorDappImpl private constructor(override val name: String, override val version: String, override val vendor: String, override val title: String, private val willResourceBeAddedToCorDapp: (String, URL) -> Boolean, private val jarEntries: Set<JarEntryInfo>) : TestCorDapp {

    constructor(name: String, version: String, vendor: String, title: String, classes: Set<Class<*>>, resources: Map<String, URL>, willResourceBeAddedToCorDapp: (String, URL) -> Boolean) : this(name, version, vendor, title, willResourceBeAddedToCorDapp, jarEntriesFromClasses(classes) + jarEntriesFromResources(resources))

    companion object {

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

        // TODO sollecitom move
        private fun jarEntriesFromResources(resources: Map<String, URL>): Set<JarEntryInfo> {

            return resources.asSequence().map { (name, url) -> JarEntryInfo.ResourceJarEntryInfo(name, url) }.toSet()
        }
    }

    override val classes: Set<Class<*>> = jarEntries.asSequence().filterIsInstance(JarEntryInfo.ClassJarEntryInfo::class.java).map(JarEntryInfo.ClassJarEntryInfo::clazz).toSet()

    override val resources: Set<URL> = jarEntries.asSequence().map(JarEntryInfo::url).toSet()

    // TODO sollecitom move
    override fun packageAsJarWithPath(jarFilePath: Path) = jarEntries.packageToCorDapp(jarFilePath, name, version, vendor, title, willResourceBeAddedToCorDapp)

    // TODO sollecitom move
    override fun packageAsJarInDirectory(parentDirectory: Path): Path = (parentDirectory / defaultJarName()).also { packageAsJarWithPath(it) }

    // TODO sollecitom move
    private fun defaultJarName(): String = "${name}_$version$jarExtension".replace(whitespace, whitespaceReplacement)
}