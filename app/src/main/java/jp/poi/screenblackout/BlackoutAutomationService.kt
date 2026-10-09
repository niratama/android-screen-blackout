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

    private var lastClickTime: Long = 0
    private var isConsumingKeyUp: Boolean = false

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

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_DOWN) {
                val clickTime = System.currentTimeMillis()
                val delta = clickTime - lastClickTime
                if (delta < DOUBLE_CLICK_TIME_DELTA) {
                    Log.i(TAG, "Volume UP double-click triggered (delta: ${delta}ms <= ${DOUBLE_CLICK_TIME_DELTA}ms)")
                    isConsumingKeyUp = true
                    lastClickTime = 0L

                    // コア機能（黒幕トグル）を実行
                    executeExistingCoreFunction()

                    // バイブレーションによる触覚フィードバック
                    vibrateFeedback()

                    return true // 音量変更イベントを消費
                }
                lastClickTime = clickTime
            } else if (action == KeyEvent.ACTION_UP) {
                if (isConsumingKeyUp) {
                    isConsumingKeyUp = false
                    return true // ダブルクリックのUPイベントも消費
                }
            }
        }

        // 通常の音量操作などはOSの標準挙動に流す
        return super.onKeyEvent(event)
    }

    /**
     * 既存の黒幕コア機能を直接実行（トグル）
     */
    private fun executeExistingCoreFunction() {
        if (BlackoutService.isRunning) {
            Log.i(TAG, "Stopping BlackoutService via volume double-click")
            val stopIntent = Intent(this, BlackoutService::class.java)
            stopService(stopIntent)
        } else {
            if (Settings.canDrawOverlays(this)) {
                Log.i(TAG, "Starting BlackoutService via volume double-click")
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
        }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate feedback failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
