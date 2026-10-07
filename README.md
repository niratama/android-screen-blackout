# ScreenBlackout

Android向け軽量画面暗転ユーティリティアプリ。
背後で動くゲーム（位置情報ゲーム等）を一時停止（`onPause`）させずに全画面を真っ黒なオーバーレイで覆い、誤タッチを遮断します。MacroDroidやADBからのBroadcastIntent経由で完全制御可能です。

---

## 主な機能
- **バックグラウンド動作 (Foreground Service):** 背面アプリの `onResume` ライフサイクルを維持。
- **全画面暗転 & 切り欠き対応:** パンチホールやノッチ領域も含めて全画面を黒色描画。
- **タッチ完全遮断:** オーバーレイがすべてのタッチイベントを消費。
- **ダブルタップ解除:** 黒幕表示中に素早くダブルタップすると黒幕を安全に解除。
- **自動化アプリ連携 (MacroDroid / ADB):** BroadcastIntent で ON/OFF/TOGGLE を制御可能。

---

## ビルド方法

```bash
./gradlew assembleDebug
```

生成されるAPK:
- `app/build/outputs/apk/debug/app-debug.apk`
- プロジェクトルート直下にもコピー配置済み: `app-debug.apk`

---

## ADBでのインストール & 制御

### 1. インストール
```bash
adb install -r app-debug.apk
```

### 2. 権限付与（オーバーレイ & 通知）
```bash
# 「他のアプリの上に重ねて表示」権限
adb shell appops set com.example.screenblackout SYSTEM_ALERT_WINDOW allow

# 通知権限 (Android 13+)
adb shell pm grant com.example.screenblackout android.permission.POST_NOTIFICATIONS
```

### 3. 操作コマンド (Broadcast)
```bash
# 黒幕 ON
adb shell am broadcast -a com.example.screenblackout.ACTION_ON -n com.example.screenblackout/.BlackoutReceiver

# 黒幕 OFF
adb shell am broadcast -a com.example.screenblackout.ACTION_OFF -n com.example.screenblackout/.BlackoutReceiver

# トグル (状態反転)
adb shell am broadcast -a com.example.screenblackout.ACTION_TOGGLE -n com.example.screenblackout/.BlackoutReceiver
```

---

## MacroDroid 設定例
- **アクション:** 「インテントを送信」
- **ターゲット:** Broadcast (ブロードキャスト)
- **アクション名:** `com.example.screenblackout.ACTION_ON` (または `ACTION_OFF`, `ACTION_TOGGLE`)
- **パッケージ名:** `com.example.screenblackout`
- **クラス名:** `com.example.screenblackout.BlackoutReceiver`
