package com.userexec.soneme.sync

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent

enum class SoftKeySlot { LEFT, CENTER, RIGHT }

abstract class SonimActivity : Activity() {
    private var lastSoftKeys: Triple<String, String, String>? = null

    protected open fun softKeyLabels(): Triple<String, String, String> = Triple("", "", "")
    protected open fun handleSoftKey(slot: SoftKeySlot) = Unit

    protected fun updateSonimSoftKeys(force: Boolean = false) {
        val labels = softKeyLabels()
        if (!force && labels == lastSoftKeys) return
        lastSoftKeys = labels
        sendBroadcast(Intent(SONIM_SOFTKEY_ACTION).apply {
            putExtra("left", labels.first)
            putExtra("center", labels.second)
            putExtra("right", labels.third)
            putExtra("from_package", packageName)
        })
    }

    override fun onResume() {
        super.onResume()
        updateSonimSoftKeys(force = true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyName = KeyEvent.keyCodeToString(event.keyCode)
        if (keyName == "KEYCODE_MULTIFUNC_LEFT") return true
        val slot = when {
            event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SOFT_LEFT -> SoftKeySlot.LEFT
            keyName == "KEYCODE_MULTIFUNC_CENTER" -> SoftKeySlot.CENTER
            keyName == "KEYCODE_MULTIFUNC_RIGHT" -> SoftKeySlot.RIGHT
            else -> null
        }
        if (slot != null) {
            if (event.action == KeyEvent.ACTION_UP) handleSoftKey(slot)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val SONIM_SOFTKEY_ACTION = "android.intent.action.CHANGE_NAV_BAR"
    }
}
