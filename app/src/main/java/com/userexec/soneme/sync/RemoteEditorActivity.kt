package com.userexec.soneme.sync

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import java.util.concurrent.Executors

class RemoteEditorActivity : SonimActivity() {
    private lateinit var db: SonemeDatabase
    private lateinit var nameField: EditText
    private lateinit var typeButton: Button
    private lateinit var addressField: EditText
    private lateinit var portField: EditText
    private lateinit var pathField: EditText
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private var editId: Long? = null
    private var type = RemoteType.SFTP
    private var testing = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_editor)
        db = SonemeDatabase(this)
        editId = intent.getLongExtra(MainActivity.EXTRA_ID, -1L).takeIf { it >= 0 }

        nameField = findViewById(R.id.nameField)
        typeButton = findViewById(R.id.typeButton)
        addressField = findViewById(R.id.addressField)
        portField = findViewById(R.id.portField)
        pathField = findViewById(R.id.pathField)
        usernameField = findViewById(R.id.usernameField)
        passwordField = findViewById(R.id.passwordField)

        editId?.let { id ->
            db.remote(id)?.let { remote ->
                nameField.setText(remote.name)
                type = remote.type
                addressField.setText(remote.address)
                portField.setText(remote.port.toString())
                pathField.setText(remote.path)
                usernameField.setText(remote.username)
                passwordField.setText(remote.password)
            }
        }
        refreshType()

        typeButton.setOnClickListener { chooseType() }
        addressField.setOnFocusChangeListener { _, focused -> if (!focused) parseAddressField() }
        val watcher = simpleTextWatcher { validate(); updateSonimSoftKeys() }
        nameField.addTextChangedListener(watcher)
        addressField.addTextChangedListener(simpleTextWatcher { validate(); updateSonimSoftKeys() })
        portField.addTextChangedListener(simpleTextWatcher { validate(); updateSonimSoftKeys() })
        usernameField.addTextChangedListener(simpleTextWatcher { validate(); updateSonimSoftKeys() })
        passwordField.addTextChangedListener(simpleTextWatcher { validate(); updateSonimSoftKeys() })
        validate()
        nameField.requestFocus()
    }

    private fun chooseType() {
        val options = arrayOf("FTP", "SFTP")
        AlertDialog.Builder(this).setTitle("Connection type")
            .setSingleChoiceItems(options, if (type == RemoteType.FTP) 0 else 1) { dialog, which ->
                type = if (which == 0) RemoteType.FTP else RemoteType.SFTP
                dialog.dismiss()
                refreshType()
                validate()
                updateSonimSoftKeys()
            }.show()
    }

    private fun refreshType() {
        typeButton.text = type.name
    }

    private fun parseAddressField() {
        val parsed = RemoteAddressParser.parse(addressField.text.toString()) ?: return
        addressField.setText(parsed.host)
        parsed.port?.let { portField.setText(it.toString()) }
        parsed.path?.let { pathField.setText(it) }
        parsed.username?.let { usernameField.setText(it) }
        parsed.password?.let { passwordField.setText(it) }
    }

    private fun defaultPort() = if (type == RemoteType.FTP) 21 else 22

    private fun currentPort(): Int? {
        val text = portField.text.toString().trim()
        if (text.isBlank()) return defaultPort()
        return text.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    private fun validate(): Boolean {
        if (!::nameField.isInitialized) return false
        val name = nameField.text.toString().trim()
        val duplicate = name.isNotBlank() && db.remoteNameExists(name, editId)
        nameField.error = if (duplicate) "Remote name already exists" else null
        val addressOk = addressField.text.toString().trim().isNotBlank()
        addressField.error = if (!addressOk && addressField.text.isNotEmpty()) "Address required" else null
        val portOk = currentPort() != null
        portField.error = if (!portOk) "Invalid port" else null
        val authOk = type == RemoteType.FTP || (usernameField.text.toString().isNotBlank() && passwordField.text.toString().isNotBlank())
        usernameField.error = if (type == RemoteType.SFTP && usernameField.text.toString().isBlank()) "Username required" else null
        passwordField.error = if (type == RemoteType.SFTP && passwordField.text.toString().isBlank()) "Password required" else null
        return name.isNotBlank() && !duplicate && addressOk && portOk && authOk
    }

    private fun valueFromFields(): RemoteFolder? {
        parseAddressField()
        if (!validate()) return null
        return RemoteFolder(
            editId ?: 0L,
            nameField.text.toString().trim(),
            type,
            addressField.text.toString().trim(),
            currentPort() ?: defaultPort(),
            pathField.text.toString(),
            usernameField.text.toString(),
            passwordField.text.toString(),
            editId?.let { db.remote(it)?.sortOrder } ?: 0
        )
    }

    override fun softKeyLabels(): Triple<String, String, String> {
        if (!::nameField.isInitialized) return Triple("", "", "")
        val valid = validate()
        return Triple(
            if (editId != null && !testing) "Delete" else "",
            if (valid && !testing) "Test" else "",
            if (valid && !testing) "Save" else ""
        )
    }

    override fun handleSoftKey(slot: SoftKeySlot) {
        when (slot) {
            SoftKeySlot.LEFT -> if (editId != null && !testing) deleteRemote()
            SoftKeySlot.CENTER -> if (!testing) testRemote()
            SoftKeySlot.RIGHT -> if (!testing) valueFromFields()?.let { db.saveRemote(editId, it); finish() }
        }
    }

    private fun testRemote() {
        val remote = valueFromFields() ?: return
        testing = true
        updateSonimSoftKeys(force = true)
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()
        executor.execute {
            val result = try {
                RemoteTester.test(remote)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
            runOnUiThread {
                testing = false
                val message = if (result.isSuccess) "Connection successful" else "Connection failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                updateSonimSoftKeys(force = true)
            }
        }
    }

    private fun deleteRemote() {
        val id = editId ?: return
        val usedBy = db.firstJobUsing(EndpointKind.REMOTE, id)
        if (usedBy != null) {
            Toast.makeText(this, "Unable to delete. Remote is used by job ${usedBy.name}.", Toast.LENGTH_LONG).show()
            return
        }
        val name = db.remote(id)?.name ?: "Remote"
        AlertDialog.Builder(this).setTitle("Delete $name?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> db.deleteRemote(id); finish() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}
