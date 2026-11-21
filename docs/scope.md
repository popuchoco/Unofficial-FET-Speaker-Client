# 規格與改造範圍

## 雛型與首版

| 能力 | 本機網頁雛型 | Android 首版 |
| --- | --- | --- |
| AI 入口 | ChatGPT、Gemini、Claude 各有官方頁面按鈕 | ChatGPT 使用 OpenAI 官方用戶端授權；Gemini 與 Claude 開啟官方頁面 |
| 帳號 | 由使用者在供應商頁面登入 | 使用者自行登入 ChatGPT 並授權個人方案用量；授權資料加密存於裝置，不設專案帳號或伺服器 |
| 對話與朗讀 | 雛型不呼叫模型 API | 首頁可用離線繁中辨識或文字輸入；送出後由手機直接呼叫 ChatGPT，完整回答以本機 TTS 輸出至藍牙媒體音箱。其他官方 AI 的文字回覆可透過 Android 分享交給本機朗讀 |
| 音箱 | 由系統配對和選擇音訊輸出 | 提供前往系統藍牙設定的入口，驗收播放及重連 |
| KKBOX | 開啟官方網站 | 可用時透過官方連結交由 KKBOX App 處理 |
| BLE | 依下方矩陣實測 | 掃描與 GATT 服務探索已實作。依兩款實測結果決定可支援的功能。 |

ChatGPT 回答採使用者明確送出後按需連線，裝置直接連 OpenAI，不經專案伺服器。短期對話脈絡只留在記憶體；Gemini、Claude 仍由使用者在官方網站登入與使用。時間、提醒、翻譯、天氣和其他明確指令先由各自本機或資料服務處理。雛型不呼叫模型 API。KKBOX 官方服務仍需網路。

## Android 介面

首頁以 Material 3 Surface 呈現藍牙媒體輸出、語音助理輸入與 ChatGPT 連結。使用者可按「開始說話」以手機麥克風離線辨識，或輸入文字；問題路由至本機功能或已連結的 ChatGPT，回答由手機 TTS 播放。測試工具提供播放、媒體按鍵與 BLE 診斷。可跟隨系統或手動指定淺色與深色，外觀偏好只存在裝置。

完整產品目標仍以音箱喚醒或按鍵為起點。收到經實測驗證的事件後，啟動相容的錄音輸入；取得回覆後由音箱播出，並顯示待命、收音、處理、回答或錯誤狀態。手機啟動的語音對話已可先行驗收；無須操作手機的喚醒、按鍵觸發與音箱麥克風流程待兩款音箱確認後接入。
## 舊版功能盤點

- 語音與文字查詢由 `OnlineDialogEngine`、`SpeechClient` 和 `OneboxSearchClient` 處理。聊天歷史同步帶帳戶 Token。社群版以官方服務入口與本機朗讀提供可獨立使用的流程。
- 舊版 KKBOX 綁定依賴遠傳服務工作階段，`KkboxApi` 本身沒有端點。首版採官方服務入口，公開 Open API 的曲庫查詢憑證和整首播放授權列在首版範圍之外。
- `PairBluetoothActivity` 開啟系統藍牙設定。裝置首次設定另有 Wi-Fi 熱點流程，列為後續實機評估。
- 小狐狸與小愛講同屬出門問問的硬體設計體系，舊版 App 有 Fox 與 Mini 的引導資源。兩款共用程式與驗收流程，分別記錄韌體、連線模式和功能結果。

## BLE 實機驗收

| 項目 | 測試方式 | 通過條件 |
| --- | --- | --- |
| 標準藍牙音訊 | 以 Android 配對、朗讀與播放 KKBOX，關閉音箱再開機 | 兩款可作媒體輸出，並能在中斷後恢復播放 |
| BLE 廣播 | 待機、藍牙配對、Wi-Fi 設定模式分別掃描 | 記錄模式、可連線性與廣播服務，不預設兩款相同 |
| GATT | 對使用者選定的音箱探索服務與特徵，核對讀取、寫入、通知與指示屬性 | 確認可讀取的標準資料後再納入產品功能 |
| 麥克風 | 在 Android 檢查可用錄音輸入並驗證來源 | 確認可取得音箱收音後再提供切換 |
| 按鍵 | 檢查系統媒體鍵與可用的 GATT 通知 | 以可重複觀察的事件決定支援範圍 |

藍牙 4.1 與舊版 APK 的配對引導都不足以證明存在可用的 BLE GATT 服務。BLE 掃描限 Android 12 以上，並在掃描和連線時請求附近裝置權限。首版核心流程使用系統媒體音訊，在 BLE 結果未明時仍可獨立驗收。

## 參考

- [Android BLE 指南](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Android 藍牙權限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [KKBOX Open API 官方 SDK](https://github.com/KKBOX/OpenAPI-JavaScript)
