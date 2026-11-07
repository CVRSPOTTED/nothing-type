package dev.nothingtype.ime

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import dev.nothingtype.R

class VoicePermissionActivity : Activity() {
    private var requesting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requesting = savedInstanceState?.getBoolean("requesting") ?: false
        if (requesting) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voice_input))
            .setMessage(getString(R.string.permission_message))
            .setPositiveButton(getString(R.string.permission_continue)) { _, _ -> requesting = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
            .setNegativeButton(getString(R.string.permission_cancel)) { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("requesting", requesting); super.onSaveInstanceState(outState) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Toast.makeText(this, if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            getString(R.string.permission_granted) else getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        finish()
    }
}
