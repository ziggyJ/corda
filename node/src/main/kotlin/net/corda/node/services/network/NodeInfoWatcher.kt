package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.internal.*
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.Change
import net.corda.node.utilities.Change.*
import net.corda.node.utilities.DirectoryWatcher
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import rx.Observable
import rx.subjects.UnicastSubject
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import kotlin.streams.toList

/**
 * Class containing the logic to
 * - Serialize and de-serialize a [NodeInfo] to disk and reading it back.
 * - Poll a directory for new serialized [NodeInfo]
 */
class NodeInfoWatcher(private val nodePath: Path) {
    companion object {
        private val logger = contextLogger()
        /**
         * Saves the given [NodeInfo] to a path.
         * The node is 'encoded' as a SignedNodeInfo, signed with the owning key of its first identity.
         * The name of the written file will be "nodeInfo-" followed by the hash of the content. The hash in the filename
         * is used so that one can freely copy these files without fearing to overwrite another one.
         *
         * @param path the path where to write the file, if non-existent it will be created.
         * @param signedNodeInfo the signed NodeInfo.
         */
        fun saveToFile(path: Path, signedNodeInfo: SignedNodeInfo) {
            try {
                path.createDirectories()
                signedNodeInfo.serialize()
                        .open()
                        .copyTo(path / "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}${signedNodeInfo.raw.hash}")
            } catch (e: Exception) {
                logger.warn("Couldn't write node info to file", e)
            }
        }
    }

    private val nodeInfosDir = (nodePath / CordformNode.NODE_INFO_DIRECTORY).createDirectories()
    private val nodeInfos = HashMap<Path, NodeInfo>()
    private val subject = UnicastSubject.create<Change<NodeInfo>>()

    fun start() {
        val directoryWatcher = DirectoryWatcher(nodeInfosDir) { file, kind ->
            if (kind == ENTRY_DELETE) {
                val nodeInfo = nodeInfos.remove(file)
                if (nodeInfo != null) {
                    subject.onNext(Remove(nodeInfo))
                } else {

                }
            } else {
                val nodeInfo = processFile(file)
                if (nodeInfo != null) {
                    nodeInfos[file] = nodeInfo
                    subject.onNext(Add(nodeInfo))
                }
            }
        }
        directoryWatcher.start()
        nodeInfosDir.list { paths -> paths.forEach {  } }
    }

    fun nodeInfoUpdates(): Observable<Change<NodeInfo>> {
        return subject
//        return Observable.interval(pollInterval.toMillis(), TimeUnit.MILLISECONDS, scheduler)
//                .flatMapIterable { loadFromDirectory() }
    }

    fun saveToFile(signedNodeInfo: SignedNodeInfo) = Companion.saveToFile(nodePath, signedNodeInfo)

    private fun processFile(file: Path): NodeInfo? {
        return try {
            logger.debug { "Reading signed NodeInfo file $file" }
            val signedData = file.readObject<SignedNodeInfo>()
            signedData.verified()
        } catch (e: Exception) {
            logger.debug("Unable to read signed NodeInfo file $file", e)
            null
        }
    }
}
