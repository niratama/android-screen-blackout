package com.example.screenblackout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class BlackoutReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON = "com.example.screenblackout.ACTION_ON"
        const val ACTION_OFF = "com.example.screenblackout.ACTION_OFF"
        const val ACTION_TOGGLE = "com.example.screenblackout.ACTION_TOGGLE"

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
                if (BlackoutService.isRunning) {
                    stopBlackout(context)
                }
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
        val serviceIntent = Intent(context, BlackoutService::class.java).apply {
            action = BlackoutService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }
}
