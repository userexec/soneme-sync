package com.userexec.soneme.sync

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import android.widget.Toast

class JobDetailActivity : SonimActivity() {
    private lateinit var db: SonemeDatabase
    private var jobId = -1L
    private var job: SyncJob? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_detail)
        db = SonemeDatabase(this)
        jobId = intent.getLongExtra(MainActivity.EXTRA_ID, -1L)
        if (jobId < 0) { finish(); return }

        val log = findViewById<TextView>(R.id.logText)
        log.movementMethod = ScrollingMovementMethod()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) refresh()
    }

    private fun refresh() {
        job = db.job(jobId)
        val value = job ?: run { finish(); return }
        val source = endpointName(value.sourceKind, value.sourceId)
        val destination = endpointName(value.destinationKind, value.destinationId)
        findViewById<TextView>(R.id.jobName).apply { text = value.name; isSelected = true }
        findViewById<TextView>(R.id.sourceName).text = source
        findViewById<TextView>(R.id.destinationName).text = destination
        findViewById<TextView>(R.id.lastRun).text = "${formatRunTime(value.lastRunAt)} — ${statusText(value.lastStatus)}"
        findViewById<TextView>(R.id.logText).apply {
            text = value.lastLog.ifBlank { "No log available" }
            post {
                val contentBottom = layout?.getLineTop(lineCount) ?: 0
                val availableHeight = height - totalPaddingTop - totalPaddingBottom
                scrollTo(0, (contentBottom - availableHeight).coerceAtLeast(0))
            }
        }
        updateSonimSoftKeys()
    }

    private fun endpointName(kind: EndpointKind, id: Long): String = when (kind) {
        EndpointKind.LOCAL -> db.local(id)?.name ?: "Missing Local"
        EndpointKind.REMOTE -> db.remote(id)?.name ?: "Missing Remote"
    }

    private fun statusText(status: RunStatus) = when (status) {
        RunStatus.NEVER -> "Never run"
        RunStatus.RUNNING -> "Running"
        RunStatus.SUCCESS -> "Success"
        RunStatus.ERROR -> "Error"
        RunStatus.CANCELED -> "Canceled"
    }

    override fun softKeyLabels() = Triple("Delete", "Edit", "Run")

    override fun handleSoftKey(slot: SoftKeySlot) {
        when (slot) {
            SoftKeySlot.LEFT -> confirmDelete()
            SoftKeySlot.CENTER -> startActivity(Intent(this, JobEditorActivity::class.java).putExtra(MainActivity.EXTRA_ID, jobId))
            SoftKeySlot.RIGHT -> runJob()
        }
    }

    private fun confirmDelete() {
        val value = job ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete ${value.name}?")
            .setMessage("This deletes the job configuration only.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> db.deleteJob(jobId); finish() }
            .show()
    }

    private fun runJob() {
        val active = SyncService.activeJobId
        if (active != null && active != jobId) {
            Toast.makeText(this, "Another sync job is already running", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, JobRunActivity::class.java).putExtra(MainActivity.EXTRA_ID, jobId))
    }

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}
