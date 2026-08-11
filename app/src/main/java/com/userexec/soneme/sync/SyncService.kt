package com.userexec.soneme.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class SyncService : Service() {
    fun interface Listener {
        fun onSnapshot(snapshot: RunSnapshot)
    }

    inner class LocalBinder : Binder() {
        fun service(): SyncService = this@SyncService
    }

    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private lateinit var db: SonemeDatabase

    @Volatile private var currentJobId: Long? = null
    @Volatile private var currentStatus = RunStatus.NEVER
    @Volatile private var currentLog = ""
    @Volatile private var worker: Thread? = null
    @Volatile private var watchdog: Thread? = null
    @Volatile private var control: RunControl? = null
    private val foregroundStarted = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        db = SonemeDatabase(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
            if (jobId >= 0) startJob(jobId)
        } else if (intent?.action == ACTION_CANCEL) {
            if (control != null) requestCancel() else stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onSnapshot(snapshot())
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun snapshot(): RunSnapshot = RunSnapshot(currentJobId, currentStatus, currentLog, currentStatus == RunStatus.RUNNING)

    fun requestCancel() {
        control?.requestStop(StopReason.CANCELED)
    }

    @Synchronized
    private fun startJob(jobId: Long) {
        val existing = worker
        if (existing?.isAlive == true) return

        val job = db.job(jobId) ?: run {
            stopSelf()
            return
        }

        currentJobId = jobId
        activeJobId = jobId
        currentStatus = RunStatus.RUNNING
        currentLog = ""
        db.beginJobRun(jobId)
        val runControl = RunControl()
        control = runControl
        startForegroundNow(jobId)
        acquireWakeLock()
        notifyListeners()

		val logger = RunLogger(onChanged = { text ->
            currentLog = text
            db.updateJobLog(jobId, text)
            notifyListeners()
        })

        worker = Thread({ runJob(job, runControl, logger) }, "SonemeSync-$jobId").also { it.start() }
        watchdog = Thread({ watchForTimeout(runControl) }, "SonemeSyncWatchdog-$jobId").also { it.start() }
    }

    private fun runJob(job: SyncJob, runControl: RunControl, logger: RunLogger) {
        var source: SyncEndpoint? = null
        var destination: SyncEndpoint? = null
        var finalStatus = RunStatus.ERROR
        try {
            logger.add("Starting job: ${job.name}")
            source = openEndpoint(job.sourceKind, job.sourceId, runControl, logger)
            destination = openEndpoint(job.destinationKind, job.destinationId, runControl, logger)
            SyncEngine(source, destination, runControl, logger).run()
            finalStatus = RunStatus.SUCCESS
        } catch (_: LogLimitException) {
            finalStatus = RunStatus.ERROR
        } catch (e: SyncStoppedException) {
            finalStatus = finishStopped(e.reason, logger)
        } catch (e: Throwable) {
            val reason = runControl.reason()
            finalStatus = if (reason != null) {
                finishStopped(reason, logger)
            } else {
                runCatching { logger.add("Error: ${messageFor(e)}") }
                RunStatus.ERROR
            }
        } finally {
            runCatching { destination?.close() }
            runCatching { source?.close() }
            finishRun(job.id, finalStatus, logger.text())
        }
    }

    private fun finishStopped(reason: StopReason, logger: RunLogger): RunStatus {
        when (reason) {
            StopReason.CANCELED -> runCatching { logger.add("Canceled") }
            StopReason.TIMEOUT -> runCatching { logger.add("Job terminated by timeout") }
        }
        return if (reason == StopReason.CANCELED) RunStatus.CANCELED else RunStatus.ERROR
    }

    private fun openEndpoint(kind: EndpointKind, id: Long, runControl: RunControl, logger: RunLogger): SyncEndpoint {
        return when (kind) {
            EndpointKind.LOCAL -> {
                val local = db.local(id) ?: throw IllegalStateException("Local configuration no longer exists")
                logger.add("Local: ${local.name}")
                DocumentsEndpoint(contentResolver, local.treeUri)
            }
            EndpointKind.REMOTE -> {
                val remote = db.remote(id) ?: throw IllegalStateException("Remote configuration no longer exists")
                logger.add("Remote: ${remote.name}")
                RemoteEndpointFactory.open(remote, runControl) { logger.add(it) }
            }
        }
    }

    @Synchronized
    private fun finishRun(jobId: Long, status: RunStatus, log: String) {
        currentStatus = status
        currentLog = log
        db.finishJobRun(jobId, status, log)
        control = null
        activeJobId = null
        notifyListeners()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted.set(false)
        releaseWakeLock()
        worker = null
        watchdog?.interrupt()
        watchdog = null
        stopSelf()
    }

    private fun watchForTimeout(runControl: RunControl) {
        try {
            while (worker?.isAlive == true && runControl.reason() == null) {
                Thread.sleep(1000)
                if (runControl.inactiveForMs() >= TIMEOUT_MS) {
                    runControl.requestStop(StopReason.TIMEOUT)
                    return
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown.
        }
    }

    private fun notifyListeners() {
        val value = snapshot()
        listeners.forEach { listener -> runCatching { listener.onSnapshot(value) } }
    }

    private fun startForegroundNow(jobId: Long) {
        val notification = buildNotification(jobId)
        if (foregroundStarted.compareAndSet(false, true)) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(jobId: Long): Notification {
        val intent = Intent(this, JobRunActivity::class.java).putExtra(MainActivity.EXTRA_ID, jobId)
        val pending = PendingIntent.getActivity(
            this,
            jobId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Soneme Sync")
            .setContentText("Running ...")
            .setOngoing(true)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sync jobs", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Foreground notification while a Soneme Sync job is running"
                setShowBadge(false)
            }
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:sync").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    override fun onDestroy() {
        control?.requestStop(StopReason.CANCELED)
        releaseWakeLock()
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    private fun messageFor(error: Throwable): String {
        var value: Throwable = error
        while (value.cause != null && value.cause !== value) value = value.cause!!
        return value.message?.takeIf { it.isNotBlank() } ?: value.javaClass.simpleName
    }

    companion object {
        const val ACTION_START = "com.userexec.soneme.sync.START"
        const val ACTION_CANCEL = "com.userexec.soneme.sync.CANCEL"
        const val EXTRA_JOB_ID = "job_id"
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 1001
        private const val TIMEOUT_MS = 60_000L

        @Volatile var activeJobId: Long? = null
            private set
    }
}
