# 規格與改造範圍

## 雛型與首版

| 能力 | 本機網頁雛型 | Android 首版 |
| --- | --- | --- |
| AI 入口 | ChatGPT、Gemini、Claude 各有官方頁面按鈕 | 以系統瀏覽器開啟相同入口 |
| 帳號 | 由使用者在供應商頁面登入 | 同一流程，無專案帳號、權杖、登入回呼或伺服器 |
| 對話與朗讀 | 使用者在官方頁面對話，貼回文字後由瀏覽器朗讀 | 貼上文字後由 Android TTS 朗讀，檢查繁中語音可用性 |
| 音箱 | 由系統配對和選擇音訊輸出 | 提供前往系統藍牙設定的入口，驗收播放及重連 |
| KKBOX | 開啟官方網站 | 可用時透過官方連結交由 KKBOX App 處理 |
| BLE | 依下方矩陣實測 | 掃描與 GATT 服務探索已實作。依兩款實測結果決定可支援的功能。 |

三家服務的網站各自處理登入與對話，使用者可在官方頁面使用帳號已開放的語音功能。社群用戶端目前以手動貼上取得待朗讀內容。雛型沒有模型 API 請求，亦不保存貼上的文字。裝置端執行指社群用戶端本身，官方 AI 和 KKBOX 服務仍需網路。

## Android 介面

以 Material 3 Surface 區分裝置、AI 服務、朗讀、音樂和 BLE 診斷。主要朗讀動作用填色按鈕，次要動作與服務連結使用較低強調樣式。可跟隨系統或手動指定淺色與深色，外觀偏好只存在裝置。BLE 操作提供掃描、連線、完成及錯誤狀態。

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

藍牙 4.1 與舊版 APK 的配對引導都不足以證明存在可用的 BLE GATT 服務。Android 12 以上的診斷頁需在掃描和連線時處理附近裝置權限。首版核心流程使用系統媒體音訊，在 BLE 結果未明時仍可獨立驗收。

## 參考

- [Android BLE 指南](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Android 藍牙權限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [KKBOX Open API 官方 SDK](https://github.com/KKBOX/OpenAPI-JavaScript)
