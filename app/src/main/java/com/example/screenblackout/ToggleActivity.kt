package com.example.screenblackout

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class ToggleActivity : Activity() {

    companion object {
        private const val TAG = "ToggleActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            Log.i(TAG, "Overlay permission not granted. Requesting overlay permission.")
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG
            ).show()

            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(permissionIntent)
        } else {
            val targetAction = if (BlackoutService.isRunning) {
                BlackoutService.ACTION_STOP
            } else {
                BlackoutService.ACTION_START
            }

            Log.i(TAG, "Overlay permission granted. Dispatching action: $targetAction")
            val serviceIntent = Intent(this, BlackoutService::class.java).apply {
                action = targetAction
            }

            if (!BlackoutService.isRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        // Theme.NoDisplay では画面を表示しないため直ちに finish() を呼び出す
        finish()
    }
}
