package net.corda.node.internal.cordapp

import co.paralleluniverse.fibers.Suspendable
import junit.framework.Assert.assertEquals
import net.corda.core.JarSignatureTestUtils.copyJar
import net.corda.core.JarSignatureTestUtils.copyRemoveExtendedLocalHeader
import net.corda.core.JarSignatureTestUtils.generateKey
import net.corda.core.JarSignatureTestUtils.stripJarSigners
import net.corda.core.JarSignatureTestUtils.manifest
import net.corda.core.JarSignatureTestUtils.jarEntry
import net.corda.core.JarSignatureTestUtils.signJar
import net.corda.core.JarSignatureTestUtils.testJar
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.node.VersionInfo
import net.corda.nodeapi.internal.DEV_PUB_KEY_HASHES
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.TestCordappDirectories
import net.corda.testing.node.internal.cordappForPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.FileInputStream
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.assertTrue

@InitiatingFlow
class DummyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow(@Suppress("UNUSED_PARAMETER") unusedSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@SchedulableFlow
class DummySchedulableFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@StartableByRPC
class DummyRPCFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

class JarScanningCordappLoaderTest {
    private companion object {
        const val isolatedContractId = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        const val isolatedFlowName = "net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator"
    }

    @Test
    fun `classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = JarScanningCordappLoader.fromDirectories(listOf(Paths.get(".")))
        assertThat(loader.cordapps).isEmpty()
    }

    @Test
    fun `isolated JAR contains a CorDapp with a contract and plugin`() {
        val isolatedJAR = JarScanningCordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(isolatedJAR))

        assertThat(loader.cordapps).hasSize(1)

        val actualCordapp = loader.cordapps.single()
        assertThat(actualCordapp.contractClassNames).isEqualTo(listOf(isolatedContractId))
        assertThat(actualCordapp.initiatedFlows.first().name).isEqualTo("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Acceptor")
        assertThat(actualCordapp.rpcFlows).isEmpty()
        assertThat(actualCordapp.schedulableFlows).isEmpty()
        assertThat(actualCordapp.services).isEmpty()
        assertThat(actualCordapp.serializationWhitelists).hasSize(1)
        assertThat(actualCordapp.serializationWhitelists.first().javaClass.name).isEqualTo("net.corda.serialization.internal.DefaultWhitelist")
        assertThat(actualCordapp.jarPath).isEqualTo(isolatedJAR)
    }

    @Test
    fun `flows are loaded by loader`() {
        val dir = TestCordappDirectories.getJarDirectory(cordappForPackages(javaClass.packageName))
        val loader = JarScanningCordappLoader.fromDirectories(listOf(dir))

        // One cordapp from this source tree. In gradle it will also pick up the node jar.
        assertThat(loader.cordapps).isNotEmpty

        val actualCordapp = loader.cordapps.single { !it.initiatedFlows.isEmpty() }
        assertThat(actualCordapp.initiatedFlows.first()).hasSameClassAs(DummyFlow::class.java)
        assertThat(actualCordapp.rpcFlows).first().hasSameClassAs(DummyRPCFlow::class.java)
        assertThat(actualCordapp.schedulableFlows).first().hasSameClassAs(DummySchedulableFlow::class.java)
    }

    // This test exists because the appClassLoader is used by serialisation and we need to ensure it is the classloader
    // being used internally. Later iterations will use a classloader per cordapp and this test can be retired.
    @Test
    fun `cordapp classloader can load cordapp classes`() {
        val isolatedJAR = JarScanningCordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(isolatedJAR), VersionInfo.UNKNOWN)

        loader.appClassLoader.loadClass(isolatedContractId)
        loader.appClassLoader.loadClass(isolatedFlowName)
    }

    @Test
    fun `cordapp classloader sets target and min version to 1 if not specified`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/no-min-or-target-version.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN)
        loader.cordapps.forEach {
            assertThat(it.info.targetPlatformVersion).isEqualTo(1)
            assertThat(it.info.minimumPlatformVersion).isEqualTo(1)
        }
    }

    @Test
    fun `cordapp classloader returns correct values for minPlatformVersion and targetVersion`() {
        // load jar with min and target version in manifest
        // make sure classloader extracts correct values
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/min-2-target-3.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN)
        val cordapp = loader.cordapps.first()
        assertThat(cordapp.info.targetPlatformVersion).isEqualTo(3)
        assertThat(cordapp.info.minimumPlatformVersion).isEqualTo(2)
    }

    @Test
    fun `cordapp classloader sets target version to min version if target version is not specified`() {
        // load jar with minVersion but not targetVersion in manifest
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/min-2-no-target.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN)
        // exclude the core cordapp
        val cordapp = loader.cordapps.single { it.cordappClasses.contains("net.corda.core.internal.cordapp.CordappImpl") }
        assertThat(cordapp.info.targetPlatformVersion).isEqualTo(2)
        assertThat(cordapp.info.minimumPlatformVersion).isEqualTo(2)
    }

    @Test
    fun `cordapp classloader does not load apps when their min platform version is greater than the node platform version`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/min-2-no-target.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN.copy(platformVersion = 1))
        assertThat(loader.cordapps).hasSize(0)
    }

    @Test
    fun `cordapp classloader does load apps when their min platform version is less than the platform version`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/min-2-target-3.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN.copy(platformVersion = 1000))
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test
    fun `cordapp classloader does load apps when their min platform version is equal to the platform version`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("versions/min-2-target-3.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), VersionInfo.UNKNOWN.copy(platformVersion = 2))
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test
    fun `cordapp classloader loads app signed by allowed certificate`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("signed/signed-by-dev-key.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), cordappsSignerKeyFingerprintBlacklist = emptyList())
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test
    fun `compare jar manifests`() {
        val jar1 = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!
        val jar2 = JarScanningCordappLoaderTest::class.java.getResource("boc-signed-copy.jar")!!
        val dir = Paths.get(jar1.toURI()).parent

        assertThat(dir.manifest(jar1.path)).isEqualTo(dir.manifest(jar2.path))
    }

    @Test
    fun `compare first jar entry`() {
//        val jar1 = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!
//        val jar2 = JarScanningCordappLoaderTest::class.java.getResource("boc-signed-copy.jar")!!
        val jar1 = JarScanningCordappLoaderTest::class.java.getResource("boc-unsigned.jar")!!
        val jar2 = JarScanningCordappLoaderTest::class.java.getResource("boc-stripped.jar")!!
        val dir = Paths.get(jar1.toURI()).parent

        val jarEntry1 = MyJarEntry(dir.jarEntry(jar1.path))
        val jarEntry2 = MyJarEntry(dir.jarEntry(jar2.path))
        jarEntry1.print()
        jarEntry2.print()
        assertTrue(jarEntry1.equals(jarEntry2))
    }

    @Test
    fun `display jar contents and metadata`() {
//        val jar = JarScanningCordappLoaderTest::class.java.getResource("boc-unsigned.jar")!!
        val jar = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!
        JarInputStream(FileInputStream(jar.toPath().toFile())).use { input ->
            while (true) {
                val entry = input.nextJarEntry ?: break
                MyJarEntry(entry).print()
            }
        }
    }

    class MyJarEntry(val ie: JarEntry) : JarEntry(ie) {
        constructor(ie: ZipEntry) : this(JarEntry(ie))

        override fun equals(other: Any?): Boolean {
            if (other is JarEntry) {
                return !((ie.creationTime != other.creationTime) ||
                        (ie.lastAccessTime != other.lastAccessTime) ||
                        (ie.lastModifiedTime != other.lastModifiedTime) ||
                        (ie.comment != other.comment) ||
                        (ie.compressedSize != other.compressedSize) ||
                        (ie.crc != other.crc) ||
                        (ie.name != other.name) ||
                        (ie.size != other.size) ||
                        (ie.time != other.time) ||
                        (ie.extra != other.extra))
            }
            return false
        }

        fun print() {
            println(""""JarEntry $ie =>
            name: ${ie.name}
            method: ${ie.method}
            creationTime: ${ie.creationTime}
            lastAccessTime: ${ie.lastAccessTime}
            lastModifiedTime: ${ie.lastModifiedTime}
            comment: ${ie.comment}
            compressedSize: ${ie.compressedSize}
            crc: ${ie.crc}
            size: ${ie.size}
            time: ${ie.time}
            extra: ${ie.extra}
        """.trimIndent())
        }
    }

    @Test
    fun `copy jar and verify same hashcode`() {
        val jarSigned = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!
        val dir = Paths.get(jarSigned.toURI()).parent

        dir.copyJar("boc-signed.jar", "boc-signed-copy.jar")
        val jarSignedCopy = (dir / "boc-signed-copy.jar").toUri().toURL()
        dir.copyJar("boc-signed.jar", "boc-signed-copy2.jar")
        val jarSignedCopy2 = (dir / "boc-signed-copy2.jar").toUri().toURL()

        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jarSigned, jarSignedCopy, jarSignedCopy2), cordappsSignerKeyFingerprintBlacklist = emptyList())
//        assertThat(loader.cordapps).hasSize(2)
        loader.cordapps.forEach { cordapp ->
            println("${cordapp.jarPath} => ${cordapp.jarHash}")
        }


    }

    @Test
    fun `compare hash of unsigned cordapp with signed cordapp stripped of certs`() {
        val jarUnsigned = JarScanningCordappLoaderTest::class.java.getResource("boc-unsigned.jar")!!
        val jarSigned = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!

        val dir = Paths.get(jarSigned.toURI()).parent
        dir.stripJarSigners("boc-signed.jar", "boc-stripped.jar")

        val jarStripped = JarScanningCordappLoaderTest::class.java.getResource("boc-stripped.jar")!!

        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jarUnsigned, jarSigned, jarStripped), cordappsSignerKeyFingerprintBlacklist = emptyList())
        assertThat(loader.cordapps).hasSize(3)
        loader.cordapps.forEach { cordapp ->
            println("${cordapp.jarPath} => ${cordapp.jarHash}")
        }

        val jarEntry1 = MyJarEntry(dir.jarEntry("boc-unsigned.jar"))
        val jarEntry2 = MyJarEntry(dir.jarEntry("boc-stripped.jar"))
        jarEntry1.print()
        jarEntry2.print()
        assertTrue(jarEntry1.equals(jarEntry2))
    }

    @Test
    fun `simple unsigned jar compare hash unsigned with signed one stripped of certs`() {
        val unsiged = "MainUnsigned.jar"
        val signed = "MainSigned.jar"
        val stripped= "MainStripped.jar"
        signStripCompare(unsiged, signed, stripped)
    }

    @Test
    fun `bank of corda cordapp compare hash unsigned with signed one stripped of certs`() {
        val unsiged = "boc-unsigned.jar"
        val signed = "boc-signed.jar"
        val stripped= "boc-stripped.jar"
        signStripCompare(unsiged, signed, stripped)
    }

    //RUN `bank of corda cordapp compare hash unsigned with signed one stripped of certs` test first to generate files
    @Test
    fun `fix bank of corda after unsiging`() {
        val jarUnsigned = JarScanningCordappLoaderTest::class.java.getResource("boc-stripped.jar")!!
        val dir = Paths.get(jarUnsigned.toURI()).parent
        dir.copyRemoveExtendedLocalHeader("boc-stripped.jar", "boc-fixed.jar")
    }

    fun signStripCompare(unsiged : String, signed : String, stripped : String) {

        val jarUnsigned = JarScanningCordappLoaderTest::class.java.getResource(unsiged)!!
        val dir = Paths.get(jarUnsigned.toURI()).parent
        (dir / signed).deleteIfExists()
        (dir / stripped).deleteIfExists()
        (dir / unsiged).copyTo((dir / signed))
        val jarSigned = JarScanningCordappLoaderTest::class.java.getResource(signed)!!

        val alias = "testAlias"
        val pwd = "testPassword"
        (dir / "_shredder").deleteIfExists()
        (dir / "_teststore").deleteIfExists()
        dir.generateKey(alias, pwd, ALICE_NAME.toString())
        dir.signJar(jarSigned.path, alias, pwd)

        dir.stripJarSigners(signed, stripped)

        val jarStripped = JarScanningCordappLoaderTest::class.java.getResource(stripped)!!

        //signed jar differs with unsigned
        with(JarScanningCordappLoader.fromJarUrls(listOf(jarUnsigned, jarSigned), cordappsSignerKeyFingerprintBlacklist = emptyList())) {
            assertThat(cordapps).hasSize(2)
            assertThat(cordapps[0].jarHash != cordapps[1].jarHash)
            assertThat(cordapps[0].jarPath != cordapps[1].jarPath)
        }

        //signed jar equals stripped one
        with(JarScanningCordappLoader.fromJarUrls(listOf(jarUnsigned, jarStripped), cordappsSignerKeyFingerprintBlacklist = emptyList())) {
            assertThat(cordapps).hasSize(2)
            assertThat(cordapps[0].jarHash == cordapps[1].jarHash)
            assertThat(cordapps[0].jarPath != cordapps[1].jarPath)
        }

        with(JarScanningCordappLoader.fromJarUrls(listOf(jarUnsigned, jarSigned, jarStripped), cordappsSignerKeyFingerprintBlacklist = emptyList())) {
            cordapps.forEach { cordapp -> println("${cordapp.jarPath} => ${cordapp.jarHash}") }
        }

        val jarEntry1 = MyJarEntry(dir.jarEntry(unsiged))
        val jarEntry2 = MyJarEntry(dir.jarEntry(stripped))
        //jarEntry1.print()
        //jarEntry2.print()
        assertEquals(jarEntry1, jarEntry2)
    }


    private fun print(jar: URL) {
        JarInputStream(FileInputStream(jar.toPath().toFile())).use { input ->
            var count = 0
            while (true) {
                val entry = input.nextJarEntry ?: break
                MyJarEntry(entry).print()
                println("COMPRESSED: ${entry.compressedSize}")
                count++
            }
            println("\n$jar has $count entries\n")
        }
    }

    private fun printAsZip(jar: URL) {
        ZipInputStream(FileInputStream(jar.toPath().toFile())).use { input ->
            var count = 0
            while (true) {
                val entry = input.nextEntry ?: break
                MyJarEntry(entry).print()
                println("COMPRESSED: ${entry.compressedSize}")
                count++
            }
            println("\n$jar has $count entries\n")
        }
    }

    private fun printAsZip2(jar: URL) {
        ZipInputStream(FileInputStream(jar.toPath().toFile())).use { input ->
            var count = 0
            var prev : ZipEntry? = null
            while (true) {
                val entry = input.nextEntry ?: break
                if(prev!=null) {
                    MyJarEntry(prev).print()
                    println("COMPRESSED: ${prev?.compressedSize}")
                }
                prev = entry
                count++
            }
            if(prev!=null) {
                MyJarEntry(prev).print()
                println("COMPRESSED: ${prev?.compressedSize}")
            }
            println("\n$jar has $count entries\n")
        }
    }

    @Test
    fun `printme hashcodes`() {
        val jarStripped = JarScanningCordappLoaderTest::class.java.getResource("boc-stripped.jar")!!
        printAsZip(jarStripped)
    }

    @Test
    fun `test jar`() {
        val jarUnsigned = JarScanningCordappLoaderTest::class.java.getResource("boc-unsigned.jar")!!
        val dir = Paths.get(jarUnsigned.toURI()).parent

        dir.testJar()
        println("================= TEST ==============")
        print((dir / "test.jar").toUri().toURL())

        println("================= TEST AS ZIP ==============")
        printAsZip((dir / "test.jar").toUri().toURL())
    }

    @Test
    fun `sign, strip and compare hashcodes`() {
        val jarUnsigned = JarScanningCordappLoaderTest::class.java.getResource("boc-unsigned.jar")!!
        printAsZip(jarUnsigned)

        val dir = Paths.get(jarUnsigned.toURI()).parent

        val jarSigned = JarScanningCordappLoaderTest::class.java.getResource("boc-signed.jar")!!
        dir.copyJar(jarUnsigned.path, jarSigned.path)
        printAsZip(jarSigned)

        val alias = "testAlias"
        val pwd = "testPassword"
        (dir / "_shredder").delete()
        (dir / "_teststore").delete()
        dir.generateKey(alias, pwd, ALICE_NAME.toString())
        val signer = dir.signJar(jarSigned.path, alias, pwd)
        printAsZip(jarSigned)

        val jarStripped = JarScanningCordappLoaderTest::class.java.getResource("boc-stripped.jar")!!
        dir.stripJarSigners(jarSigned.path, jarStripped.path)
        printAsZip(jarStripped)

//        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jarUnsigned, jarSigned, jarStripped), cordappsSignerKeyFingerprintBlacklist = emptyList())
//        assertThat(loader.cordapps).hasSize(3)
//        loader.cordapps.forEach { cordapp ->
//            println("${cordapp.jarPath} => ${cordapp.jarHash}")
//        }
    }

    @Test
    fun `cordapp classloader does not load app signed by blacklisted certificate`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("signed/signed-by-dev-key.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), cordappsSignerKeyFingerprintBlacklist = DEV_PUB_KEY_HASHES)
        assertThat(loader.cordapps).hasSize(0)
    }

    @Test
    fun `cordapp classloader loads app signed by both allowed and non-blacklisted certificate`() {
        val jar = JarScanningCordappLoaderTest::class.java.getResource("signed/signed-by-two-keys.jar")!!
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(jar), cordappsSignerKeyFingerprintBlacklist = DEV_PUB_KEY_HASHES)
        assertThat(loader.cordapps).hasSize(1)
    }
}
