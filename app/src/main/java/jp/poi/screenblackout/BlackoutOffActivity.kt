package jp.poi.screenblackout

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 黒幕をOFFにする専用の透明Activity (MacroDroidの「アクティビティを起動」用)
 */
class BlackoutOffActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopService(Intent(this, BlackoutService::class.java))
        finish()
    }
}
