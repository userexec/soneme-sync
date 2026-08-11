package com.userexec.soneme.sync

import java.io.InputStream
import java.io.OutputStream

class LogLimitException : Exception("Maximum log length exceeded")

class RunLogger(
    private val onChanged: (String) -> Unit,
    private val maxLines: Int = 2000
) {
    private val lines = mutableListOf<String>()
    private var limited = false

    @Synchronized
    fun add(message: String) {
        if (limited) throw LogLimitException()
        val incoming = message.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for (line in incoming) {
            if (lines.size >= maxLines) {
                limited = true
                lines += "Maximum log length exceeded"
                onChanged(lines.joinToString("\n"))
                throw LogLimitException()
            }
            lines += line
        }
        onChanged(lines.joinToString("\n"))
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")
}


class SyncEngine(
    private val source: SyncEndpoint,
    private val destination: SyncEndpoint,
    private val control: RunControl,
    private val log: RunLogger
) {
    fun run() {
        control.check()
        log.add("Removing partial files from destination")
        cleanPartials("")
        control.check()
        log.add("Comparing source and destination")
        syncDirectory("")
        control.check()
        log.add("Sync complete")
    }

    private fun cleanPartials(path: String) {
        control.check()
        val entries = destination.list(path)
        control.touchNetwork()
        for (entry in entries) {
            control.check()
            control.touchActivity()
            val child = childPath(path, entry.name)
            if (entry.isDirectory) {
                cleanPartials(child)
            } else if (entry.name.endsWith(PART_SUFFIX)) {
                destination.deleteFile(child)
                control.touchNetwork()
                log.add("Deleted partial: ${displayPath(child)}")
            }
        }
    }

    private fun syncDirectory(path: String) {
        control.check()
        val sourceEntries = source.list(path)
        control.touchNetwork()
        val destinationEntries = destination.list(path)
        control.touchNetwork()
        val destinationByName = destinationEntries.associateBy { it.name }

        for (sourceEntry in sourceEntries) {
            control.check()
            control.touchActivity()
            val child = childPath(path, sourceEntry.name)
            val existing = destinationByName[sourceEntry.name]

            if (existing != null) {
                if (existing.isDirectory != sourceEntry.isDirectory) {
                    log.add("Skipped name collision: ${displayPath(child)}")
                } else if (sourceEntry.isDirectory) {
                    syncDirectory(child)
                }
                continue
            }

            if (sourceEntry.isDirectory) {
                destination.createDirectory(child)
                control.touchNetwork()
                log.add("Created folder: ${displayPath(child)}")
                syncDirectory(child)
            } else {
                copyFile(child)
            }
        }
    }

    private fun copyFile(path: String) {
        control.check()
        val tempPath = childPath(parentPath(path), ".${fileName(path)}$PART_SUFFIX")
        log.add("Transferring: ${displayPath(path)}")
        source.openInput(path).use { input ->
            destination.openOutput(tempPath).use { output ->
                copyWithActivity(input, output)
            }
        }
        control.check()
        destination.rename(tempPath, path)
        control.touchNetwork()
        log.add("Transferred: ${displayPath(path)}")
    }

    private fun copyWithActivity(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            control.check()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (source.network) control.touchNetwork()
            output.write(buffer, 0, count)
            if (destination.network) control.touchNetwork()
        }
        output.flush()
        if (destination.network) control.touchNetwork()
    }

    private fun displayPath(path: String): String = if (path.isBlank()) "/" else path

    companion object {
        const val PART_SUFFIX = ".soneme-part"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
