# ScreenBlackout

Android向け軽量画面暗転ユーティリティアプリ。
背後で動くゲーム（位置情報ゲーム等）を一時停止（`onPause`）させずに全画面を真っ黒なオーバーレイで覆い、誤タッチを遮断します。MacroDroidやADBからのBroadcastIntent経由で完全制御可能です。

---

## 主な機能
- **バックグラウンド動作 (Foreground Service):** 背面アプリの `onResume` ライフサイクルを維持。
- **全画面暗転 & 切り欠き対応:** パンチホールやノッチ領域も含めて全画面を黒色描画。
- **タッチ完全遮断:** オーバーレイがすべてのタッチイベントを消費。
- **4本指タップ解除:** 黒幕表示中に画面を4本指でタップ（画面にポンと触れる）すると黒幕を安全に解除（解除時バイブレーション連動）。持ち運びや意図しない触覚による誤爆解除を強力に防止します。
- **明るさ自動調節の強制停止 ＆ システム輝度0 ＆ Extra Dim最大化:** 暗所での環境光センサーによる自動昇光を完全遮断。システム輝度を0に落とし、Extra Dimを最大強度（100%）にブースト（LCDの光漏れを極限まで低減）。解除時に元の明るさ・自動調節モードへ自動復帰。
- **音量の自動ミュート & 自動復元:** 黒幕ON時にメディア・システム音量を保存して0（消音）にし、解除時に元の音量レベルへ自動復元。
- **画面消灯・スリープ自動抑止:** 黒幕ON中は `FLAG_KEEP_SCREEN_ON` と `WakeLock` により端末のタイムアウトによる画面消灯・CPUスリープを防止（背後ゲームの常時稼働を維持）。解除時に通常スリープに復帰。
- **音量ボタンのダブルクリック（2回連続押し）で直接ON/OFF:** MacroDroid不要！
  - **音量DOWNダブルクリック（350ms以内）:** 黒幕 ON（振動1回）
  - **音量UPダブルクリック（350ms以内）:** 黒幕 OFF（振動2回）
  - 通常の1回押し音量変更はそのままOS標準動作。ダブルクリック時は音量バー表示をブロックします。
- **自動化アプリ連携 (MacroDroid / ADB):** BroadcastIntent または アクティビティ起動で ON/OFF/TOGGLE を制御可能。

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

### 3. 音量キーダブルクリック機能の有効化（ユーザー補助サービス）
端末の「設定」→「ユーザー補助」→「インストール済みのアプリ」→「ScreenBlackout」をONにするか、以下のコマンドで一発有効化できます：
```bash
# ユーザー補助サービスを有効化（既存のサービス設定を維持して追加）
CURRENT=$(adb shell settings get secure enabled_accessibility_services)
adb shell settings put secure enabled_accessibility_services "${CURRENT}:jp.poi.screenblackout/.BlackoutAutomationService"
adb shell settings put secure accessibility_enabled 1
```

### 4. 操作コマンド (Broadcast)
```bash
# 黒幕 ON
adb shell am broadcast -a jp.poi.screenblackout.ACTION_ON -n jp.poi.screenblackout/.BlackoutReceiver

# 黒幕 OFF
adb shell am broadcast -a jp.poi.screenblackout.ACTION_OFF -n jp.poi.screenblackout/.BlackoutReceiver

# トグル (状態反転)
adb shell am broadcast -a jp.poi.screenblackout.ACTION_TOGGLE -n jp.poi.screenblackout/.BlackoutReceiver
```

---

## クイック設定タイル (Quick Settings Tile)
画面上部からスワイプして表示されるクイック設定パネルに、黒幕ON用タイルを追加できます。
1. 通知バーを2回引き下げてクイック設定パネルを開く
2. 編集（鉛筆アイコン）をタップ
3. 下部のタイル候補から **「ScreenBlackout」** を上部のアクティブエリアにドラッグ＆ドロップして配置
4. ワンタップで黒幕を即座にONにできます（解除は画面4本指タップまたは音量UPダブルクリックで可能）

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
