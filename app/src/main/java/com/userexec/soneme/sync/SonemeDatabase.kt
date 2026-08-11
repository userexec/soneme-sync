package com.userexec.soneme.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SonemeDatabase(context: Context) : SQLiteOpenHelper(context, "soneme-sync.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE locals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                tree_uri TEXT NOT NULL,
                display_path TEXT NOT NULL,
                sort_order INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE remotes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                type TEXT NOT NULL,
                address TEXT NOT NULL,
                port INTEGER NOT NULL,
                path TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                sort_order INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                source_kind TEXT NOT NULL,
                source_id INTEGER NOT NULL,
                destination_kind TEXT NOT NULL,
                destination_id INTEGER NOT NULL,
                last_run_at INTEGER,
                last_status TEXT NOT NULL DEFAULT 'NEVER',
                last_log TEXT NOT NULL DEFAULT '',
                sort_order INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun locals(): List<LocalFolder> = readableDatabase.rawQuery(
        "SELECT id,name,tree_uri,display_path,sort_order FROM locals ORDER BY sort_order,id", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(LocalFolder(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)))
        }
    }

    fun remotes(): List<RemoteFolder> = readableDatabase.rawQuery(
        "SELECT id,name,type,address,port,path,username,password,sort_order FROM remotes ORDER BY sort_order,id", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                RemoteFolder(
                    c.getLong(0), c.getString(1), RemoteType.valueOf(c.getString(2)), c.getString(3), c.getInt(4),
                    c.getString(5), c.getString(6), c.getString(7), c.getInt(8)
                )
            )
        }
    }

    fun jobs(): List<SyncJob> = readableDatabase.rawQuery(
        "SELECT id,name,source_kind,source_id,destination_kind,destination_id,last_run_at,last_status,last_log,sort_order FROM jobs ORDER BY sort_order,id",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(jobFromCursor(c))
        }
    }

    fun local(id: Long): LocalFolder? = readableDatabase.rawQuery(
        "SELECT id,name,tree_uri,display_path,sort_order FROM locals WHERE id=?", arrayOf(id.toString())
    ).use { c ->
        if (!c.moveToFirst()) null else LocalFolder(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4))
    }

    fun remote(id: Long): RemoteFolder? = readableDatabase.rawQuery(
        "SELECT id,name,type,address,port,path,username,password,sort_order FROM remotes WHERE id=?", arrayOf(id.toString())
    ).use { c ->
        if (!c.moveToFirst()) null else RemoteFolder(
            c.getLong(0), c.getString(1), RemoteType.valueOf(c.getString(2)), c.getString(3), c.getInt(4),
            c.getString(5), c.getString(6), c.getString(7), c.getInt(8)
        )
    }

    fun job(id: Long): SyncJob? = readableDatabase.rawQuery(
        "SELECT id,name,source_kind,source_id,destination_kind,destination_id,last_run_at,last_status,last_log,sort_order FROM jobs WHERE id=?",
        arrayOf(id.toString())
    ).use { c -> if (c.moveToFirst()) jobFromCursor(c) else null }

    private fun jobFromCursor(c: android.database.Cursor) = SyncJob(
        c.getLong(0), c.getString(1), EndpointKind.valueOf(c.getString(2)), c.getLong(3),
        EndpointKind.valueOf(c.getString(4)), c.getLong(5), if (c.isNull(6)) null else c.getLong(6),
        RunStatus.valueOf(c.getString(7)), c.getString(8), c.getInt(9)
    )

    fun localNameExists(name: String, excludeId: Long? = null) = nameExists("locals", name, excludeId)
    fun remoteNameExists(name: String, excludeId: Long? = null) = nameExists("remotes", name, excludeId)
    fun jobNameExists(name: String, excludeId: Long? = null) = nameExists("jobs", name, excludeId)

    private fun nameExists(table: String, name: String, excludeId: Long?): Boolean {
        val sql = if (excludeId == null) {
            "SELECT 1 FROM $table WHERE name=? COLLATE NOCASE LIMIT 1"
        } else {
            "SELECT 1 FROM $table WHERE name=? COLLATE NOCASE AND id<>? LIMIT 1"
        }
        val args = if (excludeId == null) arrayOf(name) else arrayOf(name, excludeId.toString())
        return readableDatabase.rawQuery(sql, args).use { it.moveToFirst() }
    }

    fun saveLocal(id: Long?, name: String, treeUri: String, displayPath: String): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("tree_uri", treeUri)
            put("display_path", displayPath)
        }
        if (id != null) {
            writableDatabase.update("locals", values, "id=?", arrayOf(id.toString()))
            return id
        }
        values.put("sort_order", nextOrder("locals"))
        return writableDatabase.insertOrThrow("locals", null, values)
    }

    fun saveRemote(id: Long?, value: RemoteFolder): Long {
        val values = ContentValues().apply {
            put("name", value.name)
            put("type", value.type.name)
            put("address", value.address)
            put("port", value.port)
            put("path", value.path)
            put("username", value.username)
            put("password", value.password)
        }
        if (id != null) {
            writableDatabase.update("remotes", values, "id=?", arrayOf(id.toString()))
            return id
        }
        values.put("sort_order", nextOrder("remotes"))
        return writableDatabase.insertOrThrow("remotes", null, values)
    }

    fun saveJob(id: Long?, job: SyncJob): Long {
        val values = ContentValues().apply {
            put("name", job.name)
            put("source_kind", job.sourceKind.name)
            put("source_id", job.sourceId)
            put("destination_kind", job.destinationKind.name)
            put("destination_id", job.destinationId)
        }
        if (id != null) {
            writableDatabase.update("jobs", values, "id=?", arrayOf(id.toString()))
            return id
        }
        values.put("sort_order", nextOrder("jobs"))
        return writableDatabase.insertOrThrow("jobs", null, values)
    }

    fun deleteLocal(id: Long) = writableDatabase.delete("locals", "id=?", arrayOf(id.toString()))
    fun deleteRemote(id: Long) = writableDatabase.delete("remotes", "id=?", arrayOf(id.toString()))
    fun deleteJob(id: Long) = writableDatabase.delete("jobs", "id=?", arrayOf(id.toString()))

    fun firstJobUsing(kind: EndpointKind, endpointId: Long): SyncJob? {
        val sql = """SELECT id,name,source_kind,source_id,destination_kind,destination_id,last_run_at,last_status,last_log,sort_order
            FROM jobs WHERE (source_kind=? AND source_id=?) OR (destination_kind=? AND destination_id=?)
            ORDER BY sort_order,id LIMIT 1"""
        return readableDatabase.rawQuery(
            sql, arrayOf(kind.name, endpointId.toString(), kind.name, endpointId.toString())
        ).use { c -> if (c.moveToFirst()) jobFromCursor(c) else null }
    }

    fun moveUp(table: String, id: Long) {
        require(table in setOf("jobs", "locals", "remotes"))
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = db.rawQuery("SELECT sort_order FROM $table WHERE id=?", arrayOf(id.toString())).use {
                if (!it.moveToFirst()) return@use null
                it.getInt(0)
            } ?: return
            val previous = db.rawQuery(
                "SELECT id,sort_order FROM $table WHERE sort_order<? ORDER BY sort_order DESC,id DESC LIMIT 1",
                arrayOf(current.toString())
            ).use {
                if (!it.moveToFirst()) null else it.getLong(0) to it.getInt(1)
            } ?: return
            db.execSQL("UPDATE $table SET sort_order=? WHERE id=?", arrayOf(previous.second, id))
            db.execSQL("UPDATE $table SET sort_order=? WHERE id=?", arrayOf(current, previous.first))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun nextOrder(table: String): Int = readableDatabase.rawQuery(
        "SELECT COALESCE(MAX(sort_order),-1)+1 FROM $table", null
    ).use { c -> c.moveToFirst(); c.getInt(0) }

    fun beginJobRun(id: Long) {
        writableDatabase.update("jobs", ContentValues().apply {
            put("last_run_at", System.currentTimeMillis())
            put("last_status", RunStatus.RUNNING.name)
            put("last_log", "")
        }, "id=?", arrayOf(id.toString()))
    }

    fun updateJobLog(id: Long, log: String) {
        writableDatabase.update("jobs", ContentValues().apply { put("last_log", log) }, "id=?", arrayOf(id.toString()))
    }

    fun finishJobRun(id: Long, status: RunStatus, log: String) {
        writableDatabase.update("jobs", ContentValues().apply {
            put("last_status", status.name)
            put("last_log", log)
        }, "id=?", arrayOf(id.toString()))
    }

    fun markStaleRunsErrored(activeJobId: Long?) {
        val db = writableDatabase
        val ids = db.rawQuery("SELECT id,last_log FROM jobs WHERE last_status=?", arrayOf(RunStatus.RUNNING.name)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (id != activeJobId) add(id to c.getString(1))
                }
            }
        }
        ids.forEach { (id, oldLog) ->
            val line = "Job interrupted before completion"
            val log = if (oldLog.isBlank()) line else "$oldLog\n$line"
            finishJobRun(id, RunStatus.ERROR, log)
        }
    }
}
