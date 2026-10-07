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

### 2. 権限付与（オーバーレイ & 通知 & Extra Dim）
```bash
# 「他のアプリの上に重ねて表示」権限
adb shell appops set jp.poi.screenblackout SYSTEM_ALERT_WINDOW allow

# 通知権限 (Android 13+)
adb shell pm grant jp.poi.screenblackout android.permission.POST_NOTIFICATIONS

# Extra Dim（さらに輝度を下げる）制御用特権（オプション・LCD端末推奨）
adb shell pm grant jp.poi.screenblackout android.permission.WRITE_SECURE_SETTINGS
```

### 3. 操作コマンド (Broadcast)
```bash
# 黒幕 ON
adb shell am broadcast -a jp.poi.screenblackout.ACTION_ON -n jp.poi.screenblackout/.BlackoutReceiver

# 黒幕 OFF
adb shell am broadcast -a jp.poi.screenblackout.ACTION_OFF -n jp.poi.screenblackout/.BlackoutReceiver

# トグル (状態反転)
adb shell am broadcast -a jp.poi.screenblackout.ACTION_TOGGLE -n jp.poi.screenblackout/.BlackoutReceiver
```

---

## MacroDroid 設定例

### 方法 1: 「アクティビティを起動」を使う場合（推奨・簡単）
アクション追加 → **「アプリ」** → **「アクティビティを起動」** から、以下を選択するだけで制御できます。
- `jp.poi.screenblackout.BlackoutOnActivity` : 黒幕 ON
- `jp.poi.screenblackout.BlackoutOffActivity` : 黒幕 OFF
- `jp.poi.screenblackout.ToggleActivity` : トグル (反転)

### 方法 2: 「インテントを送信」を使う場合
- **アクション:** 「インテントを送信」
- **ターゲット:** Broadcast (ブロードキャスト)
- **アクション名:** `jp.poi.screenblackout.ACTION_ON` (または `ACTION_OFF`, `ACTION_TOGGLE`)
- **パッケージ名:** `jp.poi.screenblackout`
- **クラス名:** `jp.poi.screenblackout.BlackoutReceiver`
