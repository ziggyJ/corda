package net.corda.node.utilities

import com.sun.nio.file.SensitivityWatchEventModifier
import net.corda.core.internal.isDirectory
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.millis
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchEvent
import java.time.Duration
import java.util.concurrent.ScheduledFuture

/**
 * The Java directory watching API is very low level, almost a direct translation of the underlying OS API's, so we
 * wrap it here to make it more digestable. One of the things this does is buffer up rapid sequences of notifications
 * that can be caused by file copy tools like scp.
 *
 * Copied from https://github.com/vinumeris/lighthouse/blob/master/common/src/main/java/lighthouse/files/DirectoryWatcher.kt
 * with some changes.
 */
class DirectoryWatcher(private val directory: Path,
                       private val onChanged: (Path, WatchEvent.Kind<Path>) -> Unit) : AutoCloseable {
    private companion object {
        val log = contextLogger()
    }

    init {
        require(directory.isDirectory())
    }

    private val watcher = directory.fileSystem.newWatchService()

    private val thread = object : Thread("Watcher($directory)") {
        override fun run() {
            log.info("Starting directory watch service for $directory")

            try {
                while (!isInterrupted) {
                    val key = watcher.take()
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind === OVERFLOW) continue
                        @Suppress("UNCHECKED_CAST")
                        event as WatchEvent<Path>

                        val filename = (key.watchable() as Path).resolve(event.context())

                        onChanged(filename, event.kind())
                    }
                    if (!key.reset())
                        break
                }
            } catch (e: IOException) {
                log.error("Terminating watch due to error", e)
            } catch (e: InterruptedException) {
                // Shutting down ...
            }
        }
    }.apply { isDaemon = true }

    fun start() {
        directory.register(watcher, arrayOf(ENTRY_DELETE, ENTRY_CREATE, ENTRY_MODIFY), SensitivityWatchEventModifier.HIGH)
        thread.start()
    }

    override fun close() {
        thread.interrupt()
        thread.join()
        watcher.close()
    }

    // Apply a short delay to collapse rapid sequences of notifications together.
    private class Pending(val kind: WatchEvent.Kind<Path>, val future: ScheduledFuture<*>)
}
