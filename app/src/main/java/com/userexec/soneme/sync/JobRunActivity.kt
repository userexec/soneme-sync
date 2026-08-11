package com.userexec.soneme.sync

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class JobRunActivity : SonimActivity() {
    private var jobId = -1L
    private var jobNameValue = "Job"
    private var service: SyncService? = null
    private var bound = false
    private var firstBind = true
    private var startIssued = false
    private var cancelRequested = false
    private var returnScheduled = false

    private lateinit var progress: ProgressBar
    private lateinit var statusIcon: TextView
    private lateinit var statusText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val listener = SyncService.Listener { snapshot ->
        runOnUiThread { render(snapshot) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? SyncService.LocalBinder ?: return
            service = local.service()
            bound = true
            service?.addListener(listener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.removeListener(listener)
            service = null
            bound = false
            if (!isFinishing && SyncService.activeJobId == null) renderStoredResult()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_run)
        jobId = intent.getLongExtra(MainActivity.EXTRA_ID, -1L)
        if (jobId < 0) { finish(); return }

        val database = SonemeDatabase(this)
        try {
            val job = database.job(jobId) ?: run { finish(); return }
            jobNameValue = job.name
            if (savedInstanceState != null && SyncService.activeJobId == null && job.lastStatus == RunStatus.RUNNING) {
                database.markStaleRunsErrored(null)
            }
        } finally {
            database.close()
        }

        progress = findViewById(R.id.progress)
        statusIcon = findViewById(R.id.statusIcon)
        statusText = findViewById(R.id.statusText)
        logScroll = findViewById(R.id.logScroll)
        logText = findViewById(R.id.logText)
        statusText.isSelected = true

        requestNotificationsIfNeeded()
        if (savedInstanceState == null) startOrJoinJob() else if (SyncService.activeJobId != jobId) renderStoredResult()
    }

    override fun onStart() {
        super.onStart()
        val active = SyncService.activeJobId
        if (firstBind || active == jobId) {
            bindService(Intent(this, SyncService::class.java), connection, Context.BIND_AUTO_CREATE)
        } else {
            renderStoredResult()
        }
        firstBind = false
    }

    override fun onStop() {
        if (bound) {
            service?.removeListener(listener)
            unbindService(connection)
            bound = false
            service = null
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startOrJoinJob() {
        val active = SyncService.activeJobId
        if (active != null && active != jobId) {
            Toast.makeText(this, "Another sync job is already running", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (active == null) {
            startIssued = true
            startForegroundService(
                Intent(this, SyncService::class.java)
                    .setAction(SyncService.ACTION_START)
                    .putExtra(SyncService.EXTRA_JOB_ID, jobId)
            )
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun render(snapshot: RunSnapshot) {
        if (snapshot.jobId != null && snapshot.jobId != jobId) {
            Toast.makeText(this, "Another sync job is already running", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (snapshot.jobId == null && startIssued) return
        if (snapshot.status != RunStatus.RUNNING) startIssued = false

        logText.text = snapshot.log
        if (snapshot.status == RunStatus.RUNNING || snapshot.running) {
            progress.visibility = View.VISIBLE
            statusIcon.visibility = View.GONE
            statusText.text = "Running ..."
            logScroll.isFocusable = false
            logScroll.isFocusableInTouchMode = false
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        } else {
            progress.visibility = View.GONE
            statusIcon.visibility = View.VISIBLE
            when (snapshot.status) {
                RunStatus.SUCCESS -> {
                    statusIcon.text = "✓"
                    statusText.text = "$jobNameValue success."
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                    scheduleReturn()
                }
                RunStatus.ERROR -> {
                    statusIcon.text = "!"
                    statusText.text = "$jobNameValue failed."
                    enableErrorLog()
                }
                RunStatus.CANCELED -> {
                    statusIcon.text = "×"
                    statusText.text = "Canceled"
                    if (cancelRequested) finish()
                }
                RunStatus.NEVER -> {
                    statusIcon.visibility = View.GONE
                    statusText.text = "Not run"
                }
                RunStatus.RUNNING -> Unit
            }
        }
        updateSonimSoftKeys()
    }

    private fun renderStoredResult() {
        val database = SonemeDatabase(this)
        try {
            var job = database.job(jobId) ?: run { finish(); return }
            if (job.lastStatus == RunStatus.RUNNING && SyncService.activeJobId == null) {
                database.markStaleRunsErrored(null)
                job = database.job(jobId) ?: run { finish(); return }
            }
            render(RunSnapshot(job.id, job.lastStatus, job.lastLog, false))
        } finally {
            database.close()
        }
    }

    private fun enableErrorLog() {
        logScroll.isFocusable = true
        logScroll.isFocusableInTouchMode = true
        logScroll.post {
            logScroll.fullScroll(View.FOCUS_DOWN)
            logScroll.requestFocus()
        }
    }

    private fun scheduleReturn() {
        if (returnScheduled) return
        returnScheduled = true
        handler.postDelayed({ if (!isFinishing) finish() }, SUCCESS_RETURN_DELAY_MS)
    }

    override fun softKeyLabels(): Triple<String, String, String> {
        val running = service?.snapshot()?.let { it.running || it.status == RunStatus.RUNNING }
            ?: (SyncService.activeJobId == jobId || startIssued)
        return Triple(if (running) "Cancel" else "", "", "")
    }

    override fun handleSoftKey(slot: SoftKeySlot) {
        if (slot == SoftKeySlot.LEFT) cancelJob()
    }

    private fun cancelJob() {
        val running = service?.snapshot()?.let { it.running || it.status == RunStatus.RUNNING }
            ?: (SyncService.activeJobId == jobId || startIssued)
        if (!running) return
        cancelRequested = true
        service?.requestCancel() ?: startService(Intent(this, SyncService::class.java).setAction(SyncService.ACTION_CANCEL))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val running = service?.snapshot()?.let { it.running || it.status == RunStatus.RUNNING }
            ?: (SyncService.activeJobId == jobId || startIssued)
        if (running) {
            cancelRequested = true
            service?.requestCancel() ?: startService(Intent(this, SyncService::class.java).setAction(SyncService.ACTION_CANCEL))
        }
        finish()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 200
        private const val SUCCESS_RETURN_DELAY_MS = 5_000L
    }
}
