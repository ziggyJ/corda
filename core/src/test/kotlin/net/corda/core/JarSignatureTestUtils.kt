package net.corda.core

import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.loadKeyStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.util.jar.*
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import jdk.nashorn.internal.codegen.ObjectClassGenerator.pack
import java.util.jar.Pack200
import java.util.jar.Pack200.Packer






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
                println(entry)
                count++
            }
            println("\n$fileName has $count entries\n")
        }
    }

    fun Path.copyJar(inputFileName: String, outputFileName: String) {
        JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            val output = JarOutputStream(FileOutputStream((this / outputFileName).toFile()))

            if (input.manifest != null) {
                // Bizarrely the META-INF directory does not get recreated upon copying an unsigned jar ???
//                output.putNextEntry(JarEntry("META-INF"))
                val me = ZipEntry(JarFile.MANIFEST_NAME)
                output.putNextEntry(me)
                input.manifest.write(output)
                output.closeEntry()
            }

            val buffer = ByteArray(1 shl 14)
            while (true) {
                val entry = input.nextJarEntry ?: break
                println(entry)
                output.putNextEntry(entry)
                var nr: Int
                while (true) {
                    nr = input.read(buffer)
                    if (nr < 0) break
                    output.write(buffer, 0, nr)
                }
            }
            output.close()
        }
    }


    fun Path.stripJarSigners(inputFileName: String, outputFileName: String) {
       JarInputStream(FileInputStream((this / inputFileName).toFile())).use { input ->
            val output = JarOutputStream(FileOutputStream((this / outputFileName).toFile()))

            if (input.manifest != null) {
                val me = ZipEntry(JarFile.MANIFEST_NAME)
                val newManifest = Manifest()
                input.manifest.mainAttributes.forEach { entry ->
                    if (entry.key.toString() != "Sealed")
                        newManifest.mainAttributes.putValue(entry.key.toString(), entry.value.toString())
                    else
                        println("Skipping main attribute: $entry")
                }
                output.putNextEntry(me)
                newManifest.write(output)
                output.closeEntry()
            }

            val buffer = ByteArray(1 shl 14)
            while (true) {
                val entry = input.nextJarEntry ?: break
                val name = entry.name
                if (name.endsWith(".SF") ||
                        name.endsWith(".EC") ||
                        name.endsWith(".DSA") ||
                        name.endsWith(".RSA") ||
                        name.contains("SIG-*")) {
                    println("Skipping certificate entry: $entry")
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
            }
            output.close()

//           val packer = Pack200.newPacker()
//           val jarFile = JarFile((this / outputFileName).toString())
//           val fos = FileOutputStream("/tmp/test.pack")
//           // Call the packer
//           packer.pack(jarFile, fos)
//           jarFile.close()
//           fos.close()
        }
    }
}
