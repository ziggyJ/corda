package net.corda.node.internal.cordapp

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.get
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.VersionInfo
import net.corda.node.cordapp.CordappLoader
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.serialization.internal.DefaultWhitelist
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.streams.toList

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJarPaths The classpath of cordapp JARs
 */
class JarScanningCordappLoader private constructor(private val cordappJarPaths: List<URL>,
                                                   private val versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                                                   extraCordapps: List<CordappImpl>,
                                                   private val signerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()) : CordappLoaderTemplate() {
    companion object {
        private val logger = contextLogger()

        /**
         * Creates a CordappLoader from multiple directories.
         *
         * @param cordappDirs Directories used to scan for CorDapp JARs.
         */
        fun fromDirectories(cordappDirs: Collection<Path>,
                            versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                            extraCordapps: List<CordappImpl> = emptyList(),
                            signerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()): JarScanningCordappLoader {
            logger.info("Looking for CorDapps in ${cordappDirs.distinct().joinToString(", ", "[", "]")}")
            val paths = cordappDirs.distinct().flatMap(this::jarUrlsInDirectory)
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps, signerKeyFingerprintBlacklist)
        }

        /**
         * Creates a CordappLoader loader out of a list of JAR URLs.
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection.
         */
        fun fromJarUrls(scanJars: List<URL>, versionInfo: VersionInfo = VersionInfo.UNKNOWN, extraCordapps: List<CordappImpl> = emptyList(),
                        cordappsSignerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()): JarScanningCordappLoader {
            val paths = scanJars.map { it }
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps, cordappsSignerKeyFingerprintBlacklist)
        }

        private fun jarUrlsInDirectory(directory: Path): List<URL> {
            return if (!directory.exists()) {
                emptyList()
            } else {
                directory.list { paths ->
                    // `toFile()` can't be used here...
                    paths.filter { it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList()
                }
            }
        }
    }

    init {
        if (cordappJarPaths.isEmpty()) {
            logger.info("No CorDapp paths provided")
        } else {
            logger.info("Loading CorDapps from ${cordappJarPaths.joinToString()}")
        }
    }

    private val validCordappJars = cordappJarPaths
            .filter { url ->
                val manifest: Manifest? = url.openStream().use { JarInputStream(it).manifest }
                val minPlatformVersion = manifest?.get(CordappImpl.MIN_PLATFORM_VERSION)?.toIntOrNull() ?: 1
                versionInfo.platformVersion >= minPlatformVersion
            }.filter { url ->
                if (signerKeyFingerprintBlacklist.isEmpty()) {
                    true //Nothing blacklisted, no need to check
                } else {
                    val certificates = url.openStream().let(::JarInputStream).use(JarSignatureCollector::collectCertificates)
                    val blockedCertificates = certificates.filter { it.publicKey.hash.sha256() in signerKeyFingerprintBlacklist }
                    if (certificates.isEmpty() || (certificates - blockedCertificates).isNotEmpty())
                        true // Cordapp is not signed or it is signed by at least one non-blacklisted certificate
                    else {
                        logger.warn("Not loading CorDapp $url as it is signed by development key(s) only: " +
                                "${blockedCertificates.map { it.publicKey }}.")
                        false
                    }
                }
            }

    override val appClassLoader: URLClassLoader = URLClassLoader(validCordappJars.toTypedArray(), javaClass.classLoader)

    override fun close() = appClassLoader.close()

    override val cordapps: List<CordappImpl> by lazy { loadCordapps() + extraCordapps }

    private fun loadCordapps(): List<CordappImpl> {

        return ClassGraph().addClassLoader(appClassLoader).enableAnnotationInfo().enableClassInfo().pooledScan().use { scan ->

            // Scan the appClassLoader for all relevant Corda classes. This step is required because The ClassGraph scanner can't resolve dependencies between cordapps.
            val initiatedFlows = scan.getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
            val rpcFlows = scan.getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class).filter { it.isUserInvokable() }
            val serviceFlows = scan.getClassesWithAnnotation(FlowLogic::class, StartableByService::class)
            val schedulableFlows = scan.getClassesWithAnnotation(FlowLogic::class, SchedulableFlow::class)
            val allFlows = scan.getConcreteClassesOfType(FlowLogic::class)
            val services = scan.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
            val contractClassNames = coreContractClasses.flatMap { scan.getClassesImplementing(it.java.name) }.distinct().map {
                it.name.warnContractWithoutConstraintPropagation(appClassLoader)
                it.name
            }
            val serializers = scan.getClassesImplementing(SerializationCustomSerializer::class)
            val serializationWhitelists = ServiceLoader.load(SerializationWhitelist::class.java, appClassLoader).toList()
            val customSchemas = scan.getClassesWithSuperclass(MappedSchema::class).instances().toSet()
            val notaryServices = scan.getClassesWithSuperclass(NotaryService::class) + scan.getClassesWithSuperclass(SinglePartyNotaryService::class)
            logger.info("Found notary service CorDapp implementations: " + notaryServices.joinToString(", "))

            // Iterate through each cordappJarPath and create the CordappImpl based on the above classes.

            validCordappJars.map { jarUrl ->
                val manifest: Manifest? = jarUrl.openStream().use { JarInputStream(it).manifest }
                val minimumPlatformVersion = manifest?.get(CordappImpl.MIN_PLATFORM_VERSION)?.toIntOrNull() ?: 1
                val targetPlatformVersion = manifest?.get(CordappImpl.TARGET_PLATFORM_VERSION)?.toIntOrNull() ?: minimumPlatformVersion

                val allJarClasses = ClassGraph().enableClassInfo().overrideClasspath(jarUrl).pooledScan().use { it.allClasses }.map { it.name }.toSet()

                CordappImpl(
                        contractClassNames = contractClassNames.filter { it in allJarClasses },
                        initiatedFlows = initiatedFlows.filter { it.name in allJarClasses },
                        rpcFlows = rpcFlows.filter { it.name in allJarClasses },
                        serviceFlows = serviceFlows.filter { it.name in allJarClasses },
                        schedulableFlows = schedulableFlows.filter { it.name in allJarClasses },
                        services = services.filter { it.name in allJarClasses },
                        serializationWhitelists = serializationWhitelists.filter { it.javaClass.name in allJarClasses } + DefaultWhitelist,
                        serializationCustomSerializers = serializers.filter { it.javaClass.name in allJarClasses },
                        customSchemas = customSchemas.filter { it.javaClass.name in allJarClasses }.toSet(),
                        allFlows = allFlows.filter { it.name in allJarClasses },
                        jarPath = jarUrl,
                        info = parseCordappInfo(manifest, CordappImpl.jarName(jarUrl)),
                        jarHash = jarUrl.openStream().readFully().sha256(),
                        minimumPlatformVersion = minimumPlatformVersion,
                        targetPlatformVersion = targetPlatformVersion,
                        notaryService = notaryServices.filter { it.name in allJarClasses }.firstOrNull()
                )
            }
        }
    }

    private fun <T : Any> ScanResult.getClassesWithSuperclass(type: KClass<T>): List<Class<out T>> = this
            .getSubclasses(type.java.name)
            .filterNot { it.isAbstract }
            .mapNotNull { loadClass(it.name, type) }

    private fun <T : Any> ScanResult.getClassesImplementing(type: KClass<T>): List<T> = this
            .getClassesImplementing(type.java.name)
            .filterNot { it.isAbstract }
            .mapNotNull { loadClass(it.name, type) }
            .map { it.kotlin.objectOrNewInstance() }

    private fun <T : Any> ScanResult.getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> = this
            .getClassesWithAnnotation(annotation.java.name)
            .filterNot { it.isInterface }
            .filterNot { it.isAbstract }
            .mapNotNull { loadClass(it.name, type) }

    private fun <T : Any> ScanResult.getConcreteClassesOfType(type: KClass<T>): List<Class<out T>> = this
            .getSubclasses(type.java.name)
            .filterNot { it.isAbstract }
            .mapNotNull { loadClass(it.name, type) }

    private fun <T : Any> loadClass(className: String, type: KClass<T>): Class<out T>? = try {
        appClassLoader.loadClass(className).asSubclass(type.java)
    } catch (e: ClassCastException) {
        logger.warn("As $className must be a sub-type of ${type.java.name}")
        null
    } catch (e: Exception) {
        logger.warn("Unable to load class $className", e)
        null
    }

    private fun Class<out FlowLogic<*>>.isUserInvokable() = Modifier.isPublic(modifiers) && !isLocalClass && !isAnonymousClass && (!isMemberClass || Modifier.isStatic(modifiers))

    private fun <T : Any> List<Class<out T>>.instances() = map { it.kotlin.objectOrNewInstance() }

    private fun parseCordappInfo(manifest: Manifest?, defaultName: String): Cordapp.Info {
        if (manifest == null) return CordappImpl.UNKNOWN_INFO

        /** new identifiers (Corda 4) */
        // is it a Contract Jar?
        val contractInfo = if (manifest[CordappImpl.CORDAPP_CONTRACT_NAME] != null) {
            Cordapp.Info.Contract(
                    shortName = manifest[CordappImpl.CORDAPP_CONTRACT_NAME] ?: defaultName,
                    vendor = manifest[CordappImpl.CORDAPP_CONTRACT_VENDOR] ?: CordappImpl.UNKNOWN_VALUE,
                    versionId = parseVersion(manifest[CordappImpl.CORDAPP_CONTRACT_VERSION], CordappImpl.CORDAPP_CONTRACT_VERSION),
                    licence = manifest[CordappImpl.CORDAPP_CONTRACT_LICENCE] ?: CordappImpl.UNKNOWN_VALUE
            )
        } else {
            null
        }

        // is it a Workflow (flows and services) Jar?
        val workflowInfo = if (manifest[CordappImpl.CORDAPP_WORKFLOW_NAME] != null) {
            Cordapp.Info.Workflow(
                    shortName = manifest[CordappImpl.CORDAPP_WORKFLOW_NAME] ?: defaultName,
                    vendor = manifest[CordappImpl.CORDAPP_WORKFLOW_VENDOR] ?: CordappImpl.UNKNOWN_VALUE,
                    versionId = parseVersion(manifest[CordappImpl.CORDAPP_WORKFLOW_VERSION], CordappImpl.CORDAPP_WORKFLOW_VERSION),
                    licence = manifest[CordappImpl.CORDAPP_WORKFLOW_LICENCE] ?: CordappImpl.UNKNOWN_VALUE
            )
        } else {
            null
        }

        when {
            // combined Contract and Workflow Jar?
            contractInfo != null && workflowInfo != null -> return Cordapp.Info.ContractAndWorkflow(contractInfo, workflowInfo)
            contractInfo != null -> return contractInfo
            workflowInfo != null -> return workflowInfo
        }

        return Cordapp.Info.Default(
                shortName = manifest["Name"] ?: defaultName,
                vendor = manifest["Implementation-Vendor"] ?: CordappImpl.UNKNOWN_VALUE,
                version = manifest["Implementation-Version"] ?: CordappImpl.UNKNOWN_VALUE,
                licence = CordappImpl.UNKNOWN_VALUE
        )
    }

    private fun parseVersion(versionStr: String?, attributeName: String): Int {
        if (versionStr == null) {
            throw CordappInvalidVersionException("Target versionId attribute $attributeName not specified. Please specify a whole number starting from 1.")
        }
        val version = versionStr.toIntOrNull()
                ?: throw CordappInvalidVersionException("Version identifier ($versionStr) for attribute $attributeName must be a whole number starting from 1.")
        if (version < 1) {
            throw CordappInvalidVersionException("Target versionId ($versionStr) for attribute $attributeName must not be smaller than 1.")
        }
        return version
    }
}

/**
 * Thrown when scanning CorDapps.
 */
class MultipleCordappsForFlowException(message: String) : Exception(message)

/**
 * Thrown if an exception occurs whilst parsing version identifiers within cordapp configuration
 */
class CordappInvalidVersionException(msg: String) : Exception(msg)

abstract class CordappLoaderTemplate : CordappLoader {
    override val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp> by lazy {
        cordapps.flatMap { corDapp -> corDapp.allFlows.map { flow -> flow to corDapp } }
                .groupBy { it.first }
                .mapValues { entry ->
                    if (entry.value.size > 1) {
                        throw MultipleCordappsForFlowException("There are multiple CorDapp JARs on the classpath for flow " +
                                "${entry.value.first().first.name}: [ ${entry.value.joinToString { it.second.name }} ].")
                    }
                    entry.value.single().second
                }
    }

    override val cordappSchemas: Set<MappedSchema> by lazy {
        cordapps.flatMap { it.customSchemas }.toSet()
    }
}
