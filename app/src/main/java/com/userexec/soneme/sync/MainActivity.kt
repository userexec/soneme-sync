package com.userexec.soneme.sync

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : SonimActivity() {
    private enum class Tab { JOBS, LOCALS, REMOTES }

    private lateinit var db: SonemeDatabase
    private lateinit var tabBar: ViewGroup
    private lateinit var tabJobs: TextView
    private lateinit var tabLocals: TextView
    private lateinit var tabRemotes: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: SyncListAdapter
    private var currentTab = Tab.JOBS
    private val handler = Handler(Looper.getMainLooper())
    private var watchedJobId: Long? = null
    private val completionWatcher = object : Runnable {
        override fun run() {
            val watched = watchedJobId ?: return
            if (SyncService.activeJobId == watched) {
                handler.postDelayed(this, 500)
            } else {
                watchedJobId = null
                if (::db.isInitialized && !isFinishing) {
                    db.markStaleRunsErrored(SyncService.activeJobId)
                    refreshList()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = SonemeDatabase(this)
        db.markStaleRunsErrored(SyncService.activeJobId)

        tabBar = findViewById(R.id.tabBar)
        tabJobs = findViewById(R.id.tabJobs)
        tabLocals = findViewById(R.id.tabLocals)
        tabRemotes = findViewById(R.id.tabRemotes)
        listView = findViewById(R.id.mainList)
        adapter = SyncListAdapter(this)
        listView.adapter = adapter

        tabBar.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        tabBar.setOnFocusChangeListener { _, _ -> updateTabs() }
        listView.setOnFocusChangeListener { _, focused ->
            adapter.setListFocused(focused)
            updateSonimSoftKeys()
        }
        listView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                adapter.setSelectedPosition(position)
                updateSonimSoftKeys()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                adapter.setSelectedPosition(-1)
                updateSonimSoftKeys()
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            val id = adapter.row(position)?.id ?: return@setOnItemClickListener
            when (currentTab) {
                Tab.JOBS -> runJob(id)
                Tab.LOCALS -> startActivity(Intent(this, LocalEditorActivity::class.java).putExtra(EXTRA_ID, id))
                Tab.REMOTES -> startActivity(Intent(this, RemoteEditorActivity::class.java).putExtra(EXTRA_ID, id))
            }
        }

        val restoredTab = savedInstanceState?.getString(STATE_TAB)
            ?.let { runCatching { Tab.valueOf(it) }.getOrNull() }
            ?: Tab.JOBS
        showTab(restoredTab)
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) {
            db.markStaleRunsErrored(SyncService.activeJobId)
            refreshList()
            handler.removeCallbacks(completionWatcher)
            watchedJobId = SyncService.activeJobId
            if (watchedJobId != null) handler.postDelayed(completionWatcher, 500)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(completionWatcher)
        watchedJobId = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    private fun selectedId(): Long? = listView.selectedItemPosition.takeIf { it >= 0 }?.let { adapter.row(it)?.id }

    override fun softKeyLabels(): Triple<String, String, String> {
        val hasSelection = selectedId() != null
        return when (currentTab) {
            Tab.JOBS -> Triple(if (hasSelection) "Info" else "", if (canMoveUp()) "Move up" else "", "New")
            Tab.LOCALS -> Triple(if (hasSelection) "Edit" else "", if (canMoveUp()) "Move up" else "", "New")
            Tab.REMOTES -> Triple(if (hasSelection) "Edit" else "", if (canMoveUp()) "Move up" else "", "New")
        }
    }

    override fun handleSoftKey(slot: SoftKeySlot) {
        when (slot) {
            SoftKeySlot.LEFT -> selectedId()?.let { id ->
                when (currentTab) {
                    Tab.JOBS -> startActivity(Intent(this, JobDetailActivity::class.java).putExtra(EXTRA_ID, id))
                    Tab.LOCALS -> startActivity(Intent(this, LocalEditorActivity::class.java).putExtra(EXTRA_ID, id))
                    Tab.REMOTES -> startActivity(Intent(this, RemoteEditorActivity::class.java).putExtra(EXTRA_ID, id))
                }
            }
            SoftKeySlot.CENTER -> moveSelectedUp()
            SoftKeySlot.RIGHT -> createNew()
        }
    }

    private fun canMoveUp(): Boolean {
        val position = listView.selectedItemPosition
        return position > 0 && adapter.count > 0
    }

    private fun moveSelectedUp() {
        val id = selectedId() ?: return
        if (!canMoveUp()) return
        val oldPosition = listView.selectedItemPosition
        val table = when (currentTab) {
            Tab.JOBS -> "jobs"
            Tab.LOCALS -> "locals"
            Tab.REMOTES -> "remotes"
        }
        db.moveUp(table, id)
        refreshList()
        listView.setSelection((oldPosition - 1).coerceAtLeast(0))
    }

    private fun createNew() {
        when (currentTab) {
            Tab.JOBS -> {
                val noLocals = db.locals().isEmpty()
                val noRemotes = db.remotes().isEmpty()
                if (noLocals || noRemotes) {
                    val message = when {
                        noLocals && noRemotes -> "No local or remote folders available"
                        noLocals -> "No local folders available"
                        else -> "No remote folders available"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    return
                }
                startActivity(Intent(this, JobEditorActivity::class.java))
            }
            Tab.LOCALS -> startActivity(Intent(this, LocalEditorActivity::class.java))
            Tab.REMOTES -> startActivity(Intent(this, RemoteEditorActivity::class.java))
        }
    }

    private fun runJob(id: Long) {
        val active = SyncService.activeJobId
        if (active != null && active != id) {
            Toast.makeText(this, "Another sync job is already running", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, JobRunActivity::class.java).putExtra(EXTRA_ID, id))
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        updateTabs()
        refreshList()
    }

    private fun updateTabs() {
        val focused = tabBar.hasFocus()
        setTabAppearance(this, tabJobs, currentTab == Tab.JOBS, focused)
        setTabAppearance(this, tabLocals, currentTab == Tab.LOCALS, focused)
        setTabAppearance(this, tabRemotes, currentTab == Tab.REMOTES, focused)
    }

    private fun refreshList() {
        val preserveTabFocus = tabBar.hasFocus()
        val oldPosition = listView.selectedItemPosition.coerceAtLeast(0)
        val rows = when (currentTab) {
            Tab.JOBS -> db.jobs().map { RowModel(it.id, it.name, formatRunTime(it.lastRunAt), it.lastStatus) }
            Tab.LOCALS -> db.locals().map { RowModel(it.id, it.name, it.displayPath) }
            Tab.REMOTES -> db.remotes().map { RowModel(it.id, it.name, "${it.path} on ${it.address}") }
        }
        adapter.replace(rows)
        if (rows.isNotEmpty()) {
            val position = oldPosition.coerceAtMost(rows.lastIndex)
            listView.setSelection(position)
            adapter.setSelectedPosition(position)
        }
        if (preserveTabFocus) {
            tabBar.requestFocus()
            adapter.setListFocused(false)
        } else {
            listView.requestFocus()
            adapter.setListFocused(true)
        }
        updateSonimSoftKeys()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    when (currentTab) {
                        Tab.JOBS -> Unit
                        Tab.LOCALS -> showTab(Tab.JOBS)
                        Tab.REMOTES -> showTab(Tab.LOCALS)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    when (currentTab) {
                        Tab.JOBS -> showTab(Tab.LOCALS)
                        Tab.LOCALS -> showTab(Tab.REMOTES)
                        Tab.REMOTES -> Unit
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentTab) {
            Tab.JOBS -> super.onBackPressed()
            Tab.LOCALS -> showTab(Tab.JOBS)
            Tab.REMOTES -> showTab(Tab.LOCALS)
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        private const val STATE_TAB = "tab"
    }
}
