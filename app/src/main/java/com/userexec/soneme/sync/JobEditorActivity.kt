package com.userexec.soneme.sync

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class JobEditorActivity : SonimActivity() {
    private lateinit var db: SonemeDatabase
    private lateinit var nameField: EditText
    private lateinit var sourceTypeButton: Button
    private lateinit var sourceButton: Button
    private lateinit var destinationButton: Button
    private var editId: Long? = null
    private var sourceKind = EndpointKind.LOCAL
    private var sourceId: Long? = null
    private var destinationId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_editor)
        db = SonemeDatabase(this)
        editId = intent.getLongExtra(MainActivity.EXTRA_ID, -1L).takeIf { it >= 0 }

        nameField = findViewById(R.id.nameField)
        sourceTypeButton = findViewById(R.id.sourceTypeButton)
        sourceButton = findViewById(R.id.sourceButton)
        destinationButton = findViewById(R.id.destinationButton)

        editId?.let { id ->
            db.job(id)?.let { job ->
                nameField.setText(job.name)
                sourceKind = job.sourceKind
                sourceId = job.sourceId
                destinationId = job.destinationId
            }
        }
        refreshButtons()

        sourceTypeButton.setOnClickListener { chooseSourceType() }
        sourceButton.setOnClickListener { chooseEndpoint(source = true) }
        destinationButton.setOnClickListener { chooseEndpoint(source = false) }
        nameField.addTextChangedListener(simpleTextWatcher { validateName(); updateSonimSoftKeys() })
        validateName()
        nameField.requestFocus()
    }

    private fun chooseSourceType() {
        val options = arrayOf("Local", "Remote")
        AlertDialog.Builder(this).setTitle("Source type")
            .setSingleChoiceItems(options, if (sourceKind == EndpointKind.LOCAL) 0 else 1) { dialog, which ->
                sourceKind = if (which == 0) EndpointKind.LOCAL else EndpointKind.REMOTE
                sourceId = null
                destinationId = null
                dialog.dismiss()
                refreshButtons()
            }.show()
    }

    private fun chooseEndpoint(source: Boolean) {
        val kind = if (source) sourceKind else opposite(sourceKind)
        val values: List<Pair<Long, String>> = when (kind) {
            EndpointKind.LOCAL -> db.locals().map { it.id to it.name }
            EndpointKind.REMOTE -> db.remotes().map { it.id to it.name }
        }
        if (values.isEmpty()) {
            Toast.makeText(this, if (kind == EndpointKind.LOCAL) "No local folders available" else "No remote folders available", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (source) "Choose source" else "Choose destination")
            .setItems(values.map { it.second }.toTypedArray()) { _, which ->
                if (source) sourceId = values[which].first else destinationId = values[which].first
                refreshButtons()
            }.show()
    }

    private fun refreshButtons() {
        sourceTypeButton.text = if (sourceKind == EndpointKind.LOCAL) "Local" else "Remote"
        sourceButton.text = sourceId?.let { endpointName(sourceKind, it) } ?: "Choose source..."
        val destinationKind = opposite(sourceKind)
        destinationButton.text = destinationId?.let { endpointName(destinationKind, it) } ?: "Choose destination..."
        updateSonimSoftKeys()
    }

    private fun endpointName(kind: EndpointKind, id: Long): String? = when (kind) {
        EndpointKind.LOCAL -> db.local(id)?.name
        EndpointKind.REMOTE -> db.remote(id)?.name
    }

    private fun opposite(kind: EndpointKind) = if (kind == EndpointKind.LOCAL) EndpointKind.REMOTE else EndpointKind.LOCAL

    private fun validateName(): Boolean {
        val name = nameField.text.toString().trim()
        val duplicate = name.isNotBlank() && db.jobNameExists(name, editId)
        nameField.error = if (duplicate) "Job name already exists" else null
        return name.isNotBlank() && !duplicate
    }

    private fun canSave() = validateName() && sourceId != null && destinationId != null

    override fun softKeyLabels() = Triple("", "", if (::nameField.isInitialized && canSave()) "Save" else "")

    override fun handleSoftKey(slot: SoftKeySlot) {
        if (slot != SoftKeySlot.RIGHT || !canSave()) return
        val old = editId?.let(db::job)
        val value = SyncJob(
            editId ?: 0L,
            nameField.text.toString().trim(),
            sourceKind,
            sourceId!!,
            opposite(sourceKind),
            destinationId!!,
            old?.lastRunAt,
            old?.lastStatus ?: RunStatus.NEVER,
            old?.lastLog ?: "",
            old?.sortOrder ?: 0
        )
        db.saveJob(editId, value)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = finish()

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}

fun simpleTextWatcher(action: () -> Unit) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
    override fun afterTextChanged(s: Editable?) = Unit
}
