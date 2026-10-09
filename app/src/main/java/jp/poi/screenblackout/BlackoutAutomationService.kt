package jp.poi.screenblackout

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class BlackoutAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "BlackoutAutoService"
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 350 // ダブルクリック判定（ミリ秒）
    }

    private var lastUpClickTime: Long = 0
    private var lastDownClickTime: Long = 0
    private var isConsumingKeyUp: Boolean = false
    private var isConsumingKeyDown: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "BlackoutAutomationService connected and active")
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        when (keyCode) {
            // 音量UPダブルクリック: 黒幕 OFF
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    val clickTime = System.currentTimeMillis()
                    val delta = clickTime - lastUpClickTime
                    if (delta < DOUBLE_CLICK_TIME_DELTA) {
                        Log.i(TAG, "Volume UP double-click detected (delta: ${delta}ms). Turning blackout OFF.")
                        isConsumingKeyUp = true
                        lastUpClickTime = 0L

                        turnBlackoutOff()
                        vibrateFeedback(isTurnOn = false)
                        return true // 音量変更イベントを消費
                    }
                    lastUpClickTime = clickTime
                } else if (action == KeyEvent.ACTION_UP) {
                    if (isConsumingKeyUp) {
                        isConsumingKeyUp = false
                        return true // UPイベントも消費
                    }
                }
            }

            // 音量DOWNダブルクリック: 黒幕 ON
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    val clickTime = System.currentTimeMillis()
                    val delta = clickTime - lastDownClickTime
                    if (delta < DOUBLE_CLICK_TIME_DELTA) {
                        Log.i(TAG, "Volume DOWN double-click detected (delta: ${delta}ms). Turning blackout ON.")
                        isConsumingKeyDown = true
                        lastDownClickTime = 0L

                        turnBlackoutOn()
                        vibrateFeedback(isTurnOn = true)
                        return true // 音量変更イベントを消費
                    }
                    lastDownClickTime = clickTime
                } else if (action == KeyEvent.ACTION_UP) {
                    if (isConsumingKeyDown) {
                        isConsumingKeyDown = false
                        return true // UPイベントも消費
                    }
                }
            }
        }

        // 通常の音量操作などはOSの標準挙動に流す
        return super.onKeyEvent(event)
    }

    /**
     * 黒幕をONにする（すでにONの場合は何もしない）
     */
    private fun turnBlackoutOn() {
        if (!BlackoutService.isRunning) {
            if (Settings.canDrawOverlays(this)) {
                Log.i(TAG, "Starting BlackoutService via volume DOWN double-click")
                val startIntent = Intent(this, BlackoutService::class.java).apply {
                    action = BlackoutService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(startIntent)
                } else {
                    startService(startIntent)
                }
            } else {
                Log.w(TAG, "Overlay permission not granted. Cannot start Blackout.")
            }
        } else {
            Log.d(TAG, "Blackout is already running. Ignoring turn-on request.")
        }
    }

    /**
     * 黒幕をOFFにする（すでにOFFの場合は何もしない）
     */
    private fun turnBlackoutOff() {
        if (BlackoutService.isRunning) {
            Log.i(TAG, "Stopping BlackoutService via volume UP double-click")
            val stopIntent = Intent(this, BlackoutService::class.java).apply {
                action = BlackoutService.ACTION_STOP
            }
            stopService(stopIntent)
        } else {
            Log.d(TAG, "Blackout is not running. Ignoring turn-off request.")
        }
    }

    private fun vibrateFeedback(isTurnOn: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isTurnOn) {
                    // ON時: 1回振動 (80ms)
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    // OFF時: 2回振動 (50ms - 40ms - 50ms)
                    val timings = longArrayOf(0, 50, 40, 50)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (isTurnOn) {
                    vibrator?.vibrate(80)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 50, 40, 50), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate feedback failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
