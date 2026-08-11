package com.userexec.soneme.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface SyncEndpoint : Closeable {
    val network: Boolean
    fun list(path: String): List<SyncEntry>
    fun createDirectory(path: String)
    fun openInput(path: String): InputStream
    fun openOutput(path: String): OutputStream
    fun rename(fromPath: String, toPath: String)
    fun deleteFile(path: String)
    fun abort() = close()
}

fun childPath(parent: String, child: String): String = if (parent.isBlank()) child else "$parent/$child"
fun parentPath(path: String): String = path.substringBeforeLast('/', "")
fun fileName(path: String): String = path.substringAfterLast('/')

class DocumentsEndpoint(
    private val resolver: ContentResolver,
    treeUriString: String,
    override val network: Boolean = false
) : SyncEndpoint {
    private val treeUri = Uri.parse(treeUriString)
    private val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    private val cache = mutableMapOf("" to rootUri)

    override fun list(path: String): List<SyncEntry> {
        val parent = resolve(path)
        val parentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val result = mutableListOf<SyncEntry>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val name = c.getString(nameCol)
                val id = c.getString(idCol)
                val mime = c.getString(mimeCol)
                val child = childPath(path, name)
                cache[child] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                result += SyncEntry(name, mime == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        } ?: throw IllegalStateException("Unable to list local folder $path")
        return result
    }

    override fun createDirectory(path: String) {
        val parent = resolve(parentPath(path))
        val uri = DocumentsContract.createDocument(
            resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, fileName(path)
        ) ?: throw IllegalStateException("Unable to create local folder $path")
        cache[path] = uri
    }

    override fun openInput(path: String): InputStream = resolver.openInputStream(resolve(path))
        ?: throw IllegalStateException("Unable to open local file $path")

    override fun openOutput(path: String): OutputStream {
        val parent = resolve(parentPath(path))
        val uri = DocumentsContract.createDocument(
            resolver, parent, "application/octet-stream", fileName(path)
        ) ?: throw IllegalStateException("Unable to create local file $path")
        cache[path] = uri
        return resolver.openOutputStream(uri, "w") ?: throw IllegalStateException("Unable to write local file $path")
    }

    override fun rename(fromPath: String, toPath: String) {
        require(parentPath(fromPath) == parentPath(toPath)) { "Local rename must stay in the same folder" }
        val old = resolve(fromPath)
        val renamed = DocumentsContract.renameDocument(resolver, old, fileName(toPath))
            ?: throw IllegalStateException("Unable to rename local file $fromPath")
        cache.remove(fromPath)
        cache[toPath] = renamed
    }

    override fun deleteFile(path: String) {
        val uri = resolve(path)
        if (!DocumentsContract.deleteDocument(resolver, uri)) throw IllegalStateException("Unable to delete local file $path")
        cache.remove(path)
    }

    private fun resolve(path: String): Uri {
        cache[path]?.let { return it }
        var currentPath = ""
        var currentUri = rootUri
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            val nextPath = childPath(currentPath, part)
            val cached = cache[nextPath]
            if (cached != null) {
                currentPath = nextPath
                currentUri = cached
                continue
            }
            val children = list(currentPath)
            if (children.none { it.name == part }) throw IllegalStateException("Local path not found: $path")
            currentPath = nextPath
            currentUri = cache[nextPath] ?: throw IllegalStateException("Local path not found: $path")
        }
        cache[path] = currentUri
        return currentUri
    }

    override fun close() = Unit
}
