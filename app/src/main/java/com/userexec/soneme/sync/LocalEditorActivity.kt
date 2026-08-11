package com.userexec.soneme.sync

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class LocalEditorActivity : SonimActivity() {
    private lateinit var db: SonemeDatabase
    private lateinit var nameField: EditText
    private lateinit var folderButton: Button
    private lateinit var pathText: TextView
    private var editId: Long? = null
    private var treeUri: Uri? = null
    private var displayPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_editor)
        db = SonemeDatabase(this)
        editId = intent.getLongExtra(MainActivity.EXTRA_ID, -1L).takeIf { it >= 0 }
        nameField = findViewById(R.id.nameField)
        folderButton = findViewById(R.id.folderButton)
        pathText = findViewById(R.id.pathText)

        editId?.let { id ->
            db.local(id)?.let {
                nameField.setText(it.name)
                treeUri = Uri.parse(it.treeUri)
                displayPath = it.displayPath
            }
        }
        updateFolderDisplay()
        folderButton.setOnClickListener { chooseFolder() }
        nameField.addTextChangedListener(simpleTextWatcher { validateName(); updateSonimSoftKeys() })
        validateName()
        nameField.requestFocus()
    }

    private fun chooseFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }, REQUEST_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Unable to retain folder access: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        treeUri = uri
        displayPath = humanTreePath(this, uri)
        updateFolderDisplay()
        updateSonimSoftKeys()
    }

    private fun updateFolderDisplay() {
        folderButton.text = if (treeUri == null) "Choose folder..." else "Change folder..."
        pathText.text = displayPath
        pathText.isSelected = true
    }

    private fun validateName(): Boolean {
        val name = nameField.text.toString().trim()
        val duplicate = name.isNotBlank() && db.localNameExists(name, editId)
        nameField.error = if (duplicate) "Local name already exists" else null
        return name.isNotBlank() && !duplicate
    }

    private fun canSave() = validateName() && treeUri != null

    override fun softKeyLabels() = Triple(if (editId != null) "Delete" else "", "", if (::nameField.isInitialized && canSave()) "Save" else "")

    override fun handleSoftKey(slot: SoftKeySlot) {
        when (slot) {
            SoftKeySlot.LEFT -> if (editId != null) deleteLocal()
            SoftKeySlot.RIGHT -> if (canSave()) {
                db.saveLocal(editId, nameField.text.toString().trim(), treeUri.toString(), displayPath)
                finish()
            }
            else -> Unit
        }
    }

    private fun deleteLocal() {
        val id = editId ?: return
        val usedBy = db.firstJobUsing(EndpointKind.LOCAL, id)
        if (usedBy != null) {
            Toast.makeText(this, "Unable to delete. Local is used by job ${usedBy.name}.", Toast.LENGTH_LONG).show()
            return
        }
        val name = db.local(id)?.name ?: "Local"
        AlertDialog.Builder(this).setTitle("Delete $name?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> db.deleteLocal(id); finish() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = finish()

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    companion object { private const val REQUEST_FOLDER = 50 }
}
