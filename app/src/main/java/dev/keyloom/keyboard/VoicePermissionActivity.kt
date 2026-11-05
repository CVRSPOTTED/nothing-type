package dev.keyloom.keyboard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

class VoicePermissionActivity : Activity() {
    private var requesting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requesting = savedInstanceState?.getBoolean("requesting") ?: false
        if (requesting) return
        AlertDialog.Builder(this)
            .setTitle("Голосовой ввод")
            .setMessage("Нужен доступ к микрофону. Используется системная служба распознавания: если локальный движок недоступен, служба может передавать аудио на свои серверы. Nothing Type не сохраняет запись. После разрешения вернитесь в поле и нажмите «Голос» ещё раз.")
            .setPositiveButton("Продолжить") { _, _ -> requesting = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("requesting", requesting); super.onSaveInstanceState(outState) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Toast.makeText(this, if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            "Доступ разрешён. Нажмите «Голос» на клавиатуре." else "Микрофон недоступен. Разрешение можно включить в настройках Android.", Toast.LENGTH_LONG).show()
        finish()
    }
}
