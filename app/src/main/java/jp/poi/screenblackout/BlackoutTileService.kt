package jp.poi.screenblackout

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

class BlackoutTileService : TileService() {

    companion object {
        private const val TAG = "BlackoutTileService"

        fun updateTile(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, BlackoutTileService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request tile update", e)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                R.string.overlay_permission_required,
                Toast.LENGTH_LONG
            ).show()

            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    permissionIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(permissionIntent)
            }
            return
        }

        // 黒幕を開始（すでに起動中の場合はそのまま維持または同期）
        val serviceIntent = Intent(this, BlackoutService::class.java).apply {
            action = BlackoutService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (BlackoutService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
