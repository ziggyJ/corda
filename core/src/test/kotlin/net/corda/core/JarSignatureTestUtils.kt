package net.corda.core

import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.div
import net.corda.core.internal.readFully
import net.corda.finance.contracts.asset.Cash
import net.corda.nodeapi.internal.crypto.loadKeyStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.security.PublicKey
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.jar.*
import java.util.zip.*
import java.util.zip.Deflater.DEFAULT_COMPRESSION
import java.util.zip.ZipEntry.DEFLATED
import kotlin.test.assertEquals


object JarSignatureTestUtils {
    val bin = Paths.get(System.getProperty("java.home")).let { if (it.endsWith("jre")) it.parent else it } / "bin"

    fun Path.executeProcess(vararg command: String) {
        val shredder = (this / "_shredder").toFile() // No need to delete after each test.
        assertEquals(0, ProcessBuilder()
                .inheritIO()
                .redirectOutput(shredder)
                .redirectError(shredder)
                .directory(this.toFile())
                .command((bin / command[0]).toString(), *command.sliceArray(1 until command.size))
                .start()
                .waitFor())
    }

    fun Path.generateKey(alias: String, password: String, name: String) =
            executeProcess("keytool", "-genkey", "-keystore", "_teststore", "-storepass", "storepass", "-keyalg", "RSA", "-alias", alias, "-keypass", password, "-dname", name)

    fun Path.createJar(fileName: String, vararg contents: String) =
            executeProcess(*(arrayOf("jar", "cvf", fileName) + contents))

    fun Path.updateJar(fileName: String, vararg contents: String) =
            executeProcess(*(arrayOf("jar", "uvf", fileName) + contents))

    fun Path.signJar(fileName: String, alias: String, password: String): PublicKey {
        executeProcess("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", password, fileName, alias)
        val ks = loadKeyStore(this.resolve("_teststore"), "storepass")
        return ks.getCertificate(alias).publicKey
    }

    fun Path.getJarSigners(fileName: String) =
            JarInputStream(FileInputStream((this / fileName).toFile())).use(JarSignatureCollector::collectSigners)

    fun Path.printJar(fileName: String) {
        JarInputStream(FileInputStream((this / fileName).toFile())).use {
            var count = 0
            while (true) {
                val entry = it.nextJarEntry ?: break
                println("$entry, timestamps: CT=${entry.creationTime}, LAT=${entry.lastAccessTime}, LMT=${entry.lastModifiedTime}")
                count++
            }
            println("\n$fileName has $count entries\n")
        }
    }

    fun Path.copyJar(inputFileName: String, outputFileName: String) {
        ZipInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            val output = ZipOutputStream(FileOutputStream((this / outputFileName).toFile()))

            var entry= input.nextEntry

            val buffer = ByteArray(1 shl 14)
            while (true) {
                output.putNextEntry(entry)
                var nr: Int
                while (true) {
                    nr = input.read(buffer)
                    if (nr < 0) break
                    output.write(buffer, 0, nr)
                }
                entry = input.nextEntry ?: break
            }
            output.close()
        }
    }

    fun Path.manifest(inputFileName: String) : Manifest {
        JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            return input.manifest ?: throw IOException("No manifest found in jar file: $inputFileName")
        }
    }

    fun Path.jarEntry(inputFileName: String) : JarEntry {
        JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            return input.nextJarEntry ?: throw IOException("No Jar Entry found in jar file: $inputFileName")
        }
    }


    /**
     * Recreates a [ZipEntry] object. The entry's byte contents
     * will be compressed automatically, and its CRC, size and
     * compressed size fields populated.
     */
    internal fun ZipEntry.asCompressed(): ZipEntry {
        return ZipEntry(name).also { entry ->
//            entry.lastModifiedTime = lastModifiedTime
            lastAccessTime?.also { at -> entry.lastAccessTime = at }
            creationTime?.also { ct -> entry.creationTime = ct }
            entry.comment = comment
            entry.method = ZipEntry.DEFLATED
            entry.extra = extra
        }
    }

    private const val DATA_SIZE = 512

    private fun uncompressed(name: String, data: ByteArray) = ZipEntry(name).apply {
        method = ZipEntry.STORED
        compressedSize = data.size.toLong()
        size = data.size.toLong()
        crc = CRC32().let { crc ->
            crc.update(data)
            crc.value
        }
    }

    private fun compressed(name: String) = ZipEntry(name).apply { method = DEFLATED }

    internal val String.toPathFormat: String get() = replace('.', '/')
    internal val String.descriptor: String get() = "L$toPathFormat;"
    private val String.resourceName: String get() = "$toPathFormat.class"
    val Class<*>.resourceName get() = name.resourceName
    val Class<*>.bytecode: ByteArray get() = classLoader.getResourceAsStream(resourceName).use { it.readBytes() }
    val Class<*>.descriptor: String get() = name.descriptor

    private fun directoryOf(type: Class<*>)
            = directory(type.`package`.name.toPathFormat + '/')

    private fun directory(name: String) = ZipEntry(name).apply {
        method = ZipEntry.STORED
        compressedSize = 0
        size = 0
        crc = 0
    }

    fun arrayOfJunk(size: Int) = ByteArray(size).apply {
        for (i in 0 until size) {
            this[i] = (i and 0xFF).toByte()
        }
    }

    fun Path.testJar() {
        val testClass = Cash::class.java

        val manifest = Manifest().apply {
            mainAttributes.also { main ->
                main[Attributes.Name.MANIFEST_VERSION] = "1.0"
            }
        }
        val _path = this / "test.jar"
        JarOutputStream(Files.newOutputStream(_path), manifest).use { jar ->
            jar.setMethod(DEFLATED)
            jar.setLevel(DEFAULT_COMPRESSION)
            val bytes = testClass.bytecode
//            jar.putNextEntry(uncompressed(testClass.resourceName, testClass.bytecode))
            val entry = JarEntry(testClass.resourceName)
//            jar.putNextEntry(entry.asCompressed())
            jar.putNextEntry(entry)
            println("bytes to write: ${bytes.size}")
            jar.write(bytes)
            jar.closeEntry()
            jar.close()
        }
    }

    fun Path.stripJarSigners(inputFileName: String, outputFileName: String) {

//        val output = JarOutputStream(FileOutputStream((this / outputFileName).toFile()))
        val output = ZipOutputStream(FileOutputStream((this / outputFileName).toFile()))
        output.setMethod(DEFLATED)

        val appendEntries = mutableListOf<String>()
        val buffer = ByteArray(1 shl 14)

        // 1st pass
        JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->

            if (input.manifest != null) {
                val metaInf = JarEntry("META-INF/")
                metaInf.lastModifiedTime = FileTime.from(LocalDateTime.parse("2018-10-31T13:27:28").toInstant(ZoneOffset.UTC))
                output.putNextEntry(metaInf)
                output.closeEntry()

                val me = ZipEntry(JarFile.MANIFEST_NAME)
                val newManifest = Manifest()
                input.manifest.mainAttributes.forEach { entry ->
                    if (entry.key.toString() != "Sealed")
                        newManifest.mainAttributes.putValue(entry.key.toString(), entry.value.toString())
                    else
                        println("Skipping main attribute: $entry")
                }
//                me.lastModifiedTime = entry!!.lastModifiedTime
                me.lastModifiedTime = FileTime.from(LocalDateTime.parse("2018-10-31T13:27:28").toInstant(ZoneOffset.UTC))
                output.putNextEntry(me)
                newManifest.write(output)
                output.closeEntry()
            }

            ZipInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
                var entry = input.nextEntry

                while (true) {
                    val name = entry.name
                    if (name.endsWith(".SF") ||
                            name.endsWith(".EC") ||
                            name.endsWith(".DSA") ||
                            name.endsWith(".RSA") ||
                            name.contains("SIG-*")) {
                        println("Skipping certificate entry: $entry")
                        entry = input.nextEntry ?: break
                        continue
                    }

                    if ((name == "META-INF/bank-of-corda-demo.kotlin_module") ||
                            (name == "META-INF/services/") ||
                            (name == "META-INF/services/net.corda.webserver.services.WebServerPluginRegistry")) {
                        appendEntries.add(name)
                        entry = input.nextEntry ?: break
                        continue
                    } else if ((name == "META-INF/") ||
                            (name == "META-INF/MANIFEST.MF")) {
                        entry = input.nextEntry ?: break
                        continue
                    }

                    entry.lastModifiedTime = FileTime.from(LocalDateTime.parse("2018-10-31T13:27:28").toInstant(ZoneOffset.UTC))
                    val newEntry = JarEntry(entry.name)
                    output.putNextEntry(newEntry)

                    var nr: Int
                    while (true) {
                        nr = input.read(buffer)
                        if (nr < 0) break
                        output.write(buffer, 0, nr)
                    }
                    output.closeEntry()
                    entry = input.nextEntry ?: break
                }
            }

            println("Rewinding and appending ...")

            // 2nd pass
        ZipInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            var entry = input.nextEntry
            while (true) {
                if (appendEntries.contains(entry.name)) {
                    entry.lastModifiedTime = FileTime.from(LocalDateTime.parse("2018-10-31T13:27:28").toInstant(ZoneOffset.UTC))
                    output.putNextEntry(entry.asCompressed())
                    var nr: Int
                    while (true) {
                        nr = input.read(buffer)
                        if (nr < 0) break
                        output.write(buffer, 0, nr)
                    }
                }
                entry = input.nextEntry ?: break
            }
        }

            output.close()
        }

        fun Path.stripJarSigners2(inputFileName: String, outputFileName: String) {
            JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
                val output = JarOutputStream(FileOutputStream((this / outputFileName).toFile()))

                var entry = input.nextJarEntry

                if (input.manifest != null) {
                    val me = ZipEntry(JarFile.MANIFEST_NAME)
                    val newManifest = Manifest()
                    input.manifest.mainAttributes.forEach { entry ->
                        if (entry.key.toString() != "Sealed")
                            newManifest.mainAttributes.putValue(entry.key.toString(), entry.value.toString())
                        else
                            println("Skipping main attribute: $entry")
                    }
                    me.lastModifiedTime = entry!!.lastModifiedTime
                    output.putNextEntry(me)
                    newManifest.write(output)
                    output.closeEntry()
                }

                val buffer = ByteArray(1 shl 14)
                while (true) {
                    val name = entry.name
                    if (name.endsWith(".SF") ||
                            name.endsWith(".EC") ||
                            name.endsWith(".DSA") ||
                            name.endsWith(".RSA") ||
                            name.contains("SIG-*")) {
                        println("Skipping certificate entry: $entry")
                        entry = input.nextJarEntry ?: break
                        continue
                    }
                    println(entry)
                    output.putNextEntry(entry)
                    var nr: Int
                    while (true) {
                        nr = input.read(buffer)
                        if (nr < 0) break
                        output.write(buffer, 0, nr)
                    }
                    entry = input.nextJarEntry ?: break
                }
                output.close()
            }
        }
    }
}
