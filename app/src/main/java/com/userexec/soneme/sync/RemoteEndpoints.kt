package com.userexec.soneme.sync

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.Closeable
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration

object RemoteEndpointFactory {
    fun open(remote: RemoteFolder, control: RunControl, log: ((String) -> Unit)? = null): SyncEndpoint = when (remote.type) {
        RemoteType.FTP -> FtpEndpoint(remote, control, log)
        RemoteType.SFTP -> SftpEndpoint(remote, control, log)
    }
}

private class FtpEndpoint(
    remote: RemoteFolder,
    private val control: RunControl,
    log: ((String) -> Unit)?
) : SyncEndpoint {
    private val client = FTPClient()
    override val network = true
    @Volatile private var activeData: Closeable? = null
    private var useMlsd = false
    private val aborter: () -> Unit = { abort() }

    init {
        control.registerAborter(aborter)
        try {
            client.connectTimeout = 15_000
            client.defaultTimeout = 65_000
            client.setDataTimeout(Duration.ofSeconds(65))
            control.touchNetwork()
            log?.invoke("Connecting to FTP ${remote.address}:${remote.port}")
            client.connect(remote.address, remote.port)
            control.touchNetwork()
            if (!FTPReply.isPositiveCompletion(client.replyCode)) throw IllegalStateException("FTP server rejected connection: ${client.replyString.trim()}")
            client.soTimeout = 65_000
            val username = if (remote.username.isBlank() && remote.password.isBlank()) "anonymous" else remote.username
            val password = if (remote.username.isBlank() && remote.password.isBlank()) "anonymous@" else remote.password
            if (!client.login(username, password)) throw IllegalStateException("FTP authentication failed: ${client.replyString.trim()}")
            control.touchNetwork()
            client.enterLocalPassiveMode()
            client.setListHiddenFiles(true)
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw IllegalStateException("Unable to enable FTP binary mode")
            useMlsd = runCatching { client.hasFeature("MLST") || client.hasFeature("MLSD") }.getOrDefault(false)
            control.touchNetwork()
            if (remote.path.isNotBlank() && !client.changeWorkingDirectory(remote.path)) {
                throw IllegalStateException("FTP path not found or not accessible: ${remote.path}")
            }
            control.touchNetwork()
            log?.invoke("Connected")
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override fun list(path: String): List<SyncEntry> {
        control.check(); control.touchNetwork()
        val files = if (useMlsd) {
            if (path.isBlank()) client.mlistDir() else client.mlistDir(path)
        } else {
            if (path.isBlank()) client.listFiles() else client.listFiles(path)
        }
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            throw IllegalStateException("FTP list failed for ${if (path.isBlank()) "." else path}: ${client.replyString.trim()}")
        }
        control.touchNetwork()
        return files.filterNotNull().filter { it.name != "." && it.name != ".." }.map { SyncEntry(it.name, it.isDirectory) }
    }

    override fun createDirectory(path: String) {
        control.check(); control.touchNetwork()
        if (!client.makeDirectory(path)) throw IllegalStateException("FTP mkdir failed for $path: ${client.replyString.trim()}")
        control.touchNetwork()
    }

    override fun openInput(path: String): InputStream {
        control.check(); control.touchNetwork()
        val raw = client.retrieveFileStream(path) ?: throw IllegalStateException("FTP read failed for $path: ${client.replyString.trim()}")
        activeData = raw
        control.touchNetwork()
        return object : FilterInputStream(raw) {
            override fun close() {
                var first: Exception? = null
                try { super.close() } catch (e: Exception) { first = e }
                activeData = null
                try {
                    if (!client.completePendingCommand()) throw IllegalStateException("FTP read did not complete: ${client.replyString.trim()}")
                    control.touchNetwork()
                } catch (e: Exception) { if (first == null) first = e }
                first?.let { throw it }
            }
        }
    }

    override fun openOutput(path: String): OutputStream {
        control.check(); control.touchNetwork()
        val raw = client.storeFileStream(path) ?: throw IllegalStateException("FTP write failed for $path: ${client.replyString.trim()}")
        activeData = raw
        control.touchNetwork()
        return object : FilterOutputStream(raw) {
            override fun close() {
                var first: Exception? = null
                try { super.close() } catch (e: Exception) { first = e }
                activeData = null
                try {
                    if (!client.completePendingCommand()) throw IllegalStateException("FTP write did not complete: ${client.replyString.trim()}")
                    control.touchNetwork()
                } catch (e: Exception) { if (first == null) first = e }
                first?.let { throw it }
            }
        }
    }

    override fun rename(fromPath: String, toPath: String) {
        control.check(); control.touchNetwork()
        if (!client.rename(fromPath, toPath)) throw IllegalStateException("FTP rename failed for $fromPath: ${client.replyString.trim()}")
        control.touchNetwork()
    }

    override fun deleteFile(path: String) {
        control.check(); control.touchNetwork()
        if (!client.deleteFile(path)) throw IllegalStateException("FTP delete failed for $path: ${client.replyString.trim()}")
        control.touchNetwork()
    }

    override fun abort() {
        runCatching { activeData?.close() }
        activeData = null
        runCatching { if (client.isConnected) client.disconnect() }
    }

    override fun close() {
        control.unregisterAborter(aborter)
        runCatching { if (client.isConnected) client.logout() }
        runCatching { if (client.isConnected) client.disconnect() }
    }
}

private class SftpEndpoint(
    remote: RemoteFolder,
    private val control: RunControl,
    log: ((String) -> Unit)?
) : SyncEndpoint {
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    override val network = true
    private val aborter: () -> Unit = { abort() }

    init {
        control.registerAborter(aborter)
        try {
            log?.invoke("Connecting to SFTP ${remote.address}:${remote.port}")
            control.touchNetwork()
            val s = JSch().getSession(remote.username, remote.address, remote.port)
            session = s
            s.setPassword(remote.password)
            s.setConfig("StrictHostKeyChecking", "no")
            s.setConfig("PreferredAuthentications", "password")
            s.timeout = 65_000
            s.connect(15_000)
            control.touchNetwork()
            val c = s.openChannel("sftp") as ChannelSftp
            channel = c
            c.connect(15_000)
            control.touchNetwork()
            if (remote.path.isNotBlank()) c.cd(remote.path)
            control.touchNetwork()
            log?.invoke("Connected")
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun c(): ChannelSftp = channel ?: throw IllegalStateException("SFTP channel is closed")

    override fun list(path: String): List<SyncEntry> {
        control.check(); control.touchNetwork()
        val raw = c().ls(if (path.isBlank()) "." else path)
        control.touchNetwork()
        return raw.mapNotNull { item ->
            val entry = item as? ChannelSftp.LsEntry ?: return@mapNotNull null
            if (entry.filename == "." || entry.filename == "..") null else SyncEntry(entry.filename, entry.attrs.isDir)
        }
    }

    override fun createDirectory(path: String) {
        control.check(); control.touchNetwork(); c().mkdir(path); control.touchNetwork()
    }

    override fun openInput(path: String): InputStream {
        control.check(); control.touchNetwork()
        return c().get(path).also { control.touchNetwork() }
    }

    override fun openOutput(path: String): OutputStream {
        control.check(); control.touchNetwork()
        return c().put(path).also { control.touchNetwork() }
    }

    override fun rename(fromPath: String, toPath: String) {
        control.check(); control.touchNetwork(); c().rename(fromPath, toPath); control.touchNetwork()
    }

    override fun deleteFile(path: String) {
        control.check(); control.touchNetwork(); c().rm(path); control.touchNetwork()
    }

    override fun abort() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
    }

    override fun close() {
        control.unregisterAborter(aborter)
        abort()
    }
}

object RemoteTester {
    fun test(remote: RemoteFolder) {
        val control = RunControl()
        RemoteEndpointFactory.open(remote, control).use { it.list("") }
    }
}
