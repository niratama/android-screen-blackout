package jp.poi.screenblackout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class BlackoutService : Service() {

    companion object {
        const val ACTION_START = "jp.poi.screenblackout.ACTION_START"
        const val ACTION_STOP = "jp.poi.screenblackout.ACTION_STOP"

        private const val CHANNEL_ID = "screen_blackout_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BlackoutService"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var blackoutView: View? = null
    private var wasExtraDimEnabledInitially: Boolean? = null
    private var originalBrightnessMode: Int? = null
    private var originalBrightness: Int? = null
    private val savedVolumes = mutableMapOf<Int, Int>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand received action: $action")

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot start blackout overlay: SYSTEM_ALERT_WINDOW permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        showBlackoutOverlay()

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, BlackoutService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBlackoutOverlay() {
        if (blackoutView != null) {
            return
        }

        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 4本指タップ（マルチタッチ）で解除。1〜3本指のタッチイベントは消費して背後アプリへの誤タップを遮断
        view.setOnTouchListener { _, event ->
            if (event.pointerCount >= 4) {
                Log.i(TAG, "4-finger touch detected (pointerCount=${event.pointerCount}), dismissing blackout overlay")
                vibrateDismissFeedback()
                stopSelf()
            }
            true
        }

        @Suppress("DEPRECATION")
        view.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.FILL
            screenBrightness = 0.0f // バックライト輝度を最小値に設定（解除時に自動復帰）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // View側でも画面点灯維持フラグをセット
        view.keepScreenOn = true

        try {
            windowManager.addView(view, params)
            blackoutView = view
            isRunning = true
            Log.d(TAG, "Blackout overlay successfully added to WindowManager")
            acquireWakeLock()
            applyMaximumDimming()
            muteVolumes()
            BlackoutTileService.updateTile(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add blackout overlay", e)
            stopSelf()
        }
    }

    private fun vibrateDismissFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 50, 40, 50)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 50, 40, 50), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate feedback failed", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ScreenBlackout:WakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 最大12時間の安全タイムアウト
                Log.i(TAG, "WakeLock acquired successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
        wakeLock = null
    }

    private fun muteVolumes() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val streamsToMute = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_SYSTEM
        )
        savedVolumes.clear()
        for (stream in streamsToMute) {
            try {
                val current = audioManager.getStreamVolume(stream)
                savedVolumes[stream] = current
                if (current > 0) {
                    audioManager.setStreamVolume(stream, 0, 0)
                    Log.i(TAG, "Muted audio stream $stream (was $current)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mute audio stream $stream", e)
            }
        }
    }

    private fun restoreVolumes() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        for ((stream, originalVolume) in savedVolumes) {
            try {
                audioManager.setStreamVolume(stream, originalVolume, 0)
                Log.i(TAG, "Restored audio stream $stream to $originalVolume")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore audio stream $stream to $originalVolume", e)
            }
        }
        savedVolumes.clear()
    }

    private fun applyMaximumDimming() {
        // 1. システムの明るさ自動調節をOFFにし、システム輝度を0にする
        try {
            val mode = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            originalBrightnessMode = mode

            val brightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            originalBrightness = brightness

            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Log.i(TAG, "Disabled adaptive brightness (SCREEN_BRIGHTNESS_MODE_MANUAL)")
            }

            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                0
            )
            Log.i(TAG, "Set system screen brightness to 0 (was $brightness)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust system brightness/mode (requires WRITE_SETTINGS/WRITE_SECURE_SETTINGS)", e)
        }

        // 2. Extra Dim を有効化し、強度を最大（100%）にブースト
        try {
            val activatedResult = Settings.Secure.putInt(
                contentResolver,
                "reduce_bright_colors_activated",
                1
            )
            val levelResult = Settings.Secure.putInt(
                contentResolver,
                "reduce_bright_colors_level",
                100
            )
            Log.i(TAG, "Extra Dim enabled (result=$activatedResult) & level set to 100% (result=$levelResult)")
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted. Skipping Extra Dim control.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Extra Dim settings", e)
        }
    }

    private fun restoreBrightnessAndDimming() {
        // 1. Extra Dim の復元
        try {
            val result = Settings.Secure.putInt(
                contentResolver,
                "reduce_bright_colors_activated",
                0
            )
            Settings.Secure.putInt(
                contentResolver,
                "reduce_bright_colors_level",
                50
            )
            Log.i(TAG, "Extra Dim restored to normal (OFF, level 50%) (result=$result)")
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted. Skipping Extra Dim restore.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore Extra Dim setting", e)
        }

        // 2. システム輝度と自動調節モードの復元
        try {
            originalBrightness?.let { prevBrightness ->
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    prevBrightness
                )
                Log.i(TAG, "Restored system screen brightness to $prevBrightness")
            }

            originalBrightnessMode?.let { prevMode ->
                if (prevMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    )
                    Log.i(TAG, "Restored adaptive brightness mode (AUTOMATIC)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore system brightness settings", e)
        }

        originalBrightnessMode = null
        originalBrightness = null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        restoreBrightnessAndDimming()
        restoreVolumes()
        BlackoutTileService.updateTile(this)
        val view = blackoutView
        if (view != null) {
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                    Log.d(TAG, "Blackout overlay successfully removed from WindowManager")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing blackout overlay view", e)
            }
        }
        blackoutView = null
        Log.d(TAG, "BlackoutService destroyed")
    }
}
