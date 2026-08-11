package com.userexec.soneme.sync

import java.util.concurrent.CopyOnWriteArrayList

enum class StopReason { CANCELED, TIMEOUT }

class SyncStoppedException(val reason: StopReason) : Exception(reason.name)

class RunControl {
    @Volatile private var stopReason: StopReason? = null
    @Volatile private var lastActivityMs: Long = nowMs()
    private val aborters = CopyOnWriteArrayList<() -> Unit>()

    fun touchActivity() {
        lastActivityMs = nowMs()
        check()
    }

    fun touchNetwork() = touchActivity()

    fun inactiveForMs(): Long = (nowMs() - lastActivityMs).coerceAtLeast(0L)

    fun check() {
        stopReason?.let { throw SyncStoppedException(it) }
    }

    fun reason(): StopReason? = stopReason

    fun registerAborter(aborter: () -> Unit) {
        aborters += aborter
        if (stopReason != null) runCatching(aborter)
    }

    fun unregisterAborter(aborter: () -> Unit) {
        aborters -= aborter
    }

    fun requestStop(reason: StopReason) {
        synchronized(this) {
            if (stopReason != null) return
            stopReason = reason
        }
        aborters.forEach { runCatching(it) }
    }

    companion object {
        private fun nowMs(): Long = System.nanoTime() / 1_000_000L
    }
}
