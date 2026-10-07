package jp.poi.screenblackout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class BlackoutReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON = "jp.poi.screenblackout.ACTION_ON"
        const val ACTION_OFF = "jp.poi.screenblackout.ACTION_OFF"
        const val ACTION_TOGGLE = "jp.poi.screenblackout.ACTION_TOGGLE"

        private const val TAG = "BlackoutReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Broadcast received: $action (current isRunning=${BlackoutService.isRunning})")

        when (action) {
            ACTION_ON -> {
                if (!BlackoutService.isRunning) {
                    if (Settings.canDrawOverlays(context)) {
                        startBlackout(context)
                    } else {
                        Log.w(TAG, "Cannot start blackout: SYSTEM_ALERT_WINDOW permission is missing")
                    }
                }
            }
            ACTION_OFF -> {
                Log.i(TAG, "Stopping blackout unconditionally")
                stopBlackout(context)
            }
            ACTION_TOGGLE -> {
                if (BlackoutService.isRunning) {
                    stopBlackout(context)
                } else {
                    if (Settings.canDrawOverlays(context)) {
                        startBlackout(context)
                    } else {
                        Log.w(TAG, "Cannot start blackout: SYSTEM_ALERT_WINDOW permission is missing")
                    }
                }
            }
        }
    }

    private fun startBlackout(context: Context) {
        val serviceIntent = Intent(context, BlackoutService::class.java).apply {
            action = BlackoutService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopBlackout(context: Context) {
        // バックグラウンド制限を受けない直接のstopServiceで確実に停止
        val serviceIntent = Intent(context, BlackoutService::class.java)
        context.stopService(serviceIntent)
    }
}
