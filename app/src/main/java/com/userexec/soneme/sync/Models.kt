package com.userexec.soneme.sync

enum class EndpointKind { LOCAL, REMOTE }
enum class RemoteType { FTP, SFTP }
enum class RunStatus { NEVER, RUNNING, SUCCESS, ERROR, CANCELED }

data class LocalFolder(
    val id: Long,
    val name: String,
    val treeUri: String,
    val displayPath: String,
    val sortOrder: Int
)

data class RemoteFolder(
    val id: Long,
    val name: String,
    val type: RemoteType,
    val address: String,
    val port: Int,
    val path: String,
    val username: String,
    val password: String,
    val sortOrder: Int
)

data class SyncJob(
    val id: Long,
    val name: String,
    val sourceKind: EndpointKind,
    val sourceId: Long,
    val destinationKind: EndpointKind,
    val destinationId: Long,
    val lastRunAt: Long?,
    val lastStatus: RunStatus,
    val lastLog: String,
    val sortOrder: Int
)

data class SyncEntry(val name: String, val isDirectory: Boolean)

data class RunSnapshot(
    val jobId: Long?,
    val status: RunStatus,
    val log: String,
    val running: Boolean
)
