# 愛講音箱社群版

支援小狐狸與小愛講的獨立 Android 用戶端。App 在使用者裝置上執行，無須伺服器或遠傳會員帳號。AI 對話及 KKBOX 播放交由各家官方服務處理。

## 使用 Android 版

1. 在 Android 系統設定配對音箱，選為媒體音訊輸出。
2. 支援離線辨識的 Android 12 以上裝置可在「語音提問」說出問題，再編輯和複製辨識文字。也可直接輸入。
3. 從 App 開啟 ChatGPT、Gemini 或 Claude，在官方頁面自行登入、貼上問題並對話。
4. 把回覆貼到「朗讀回覆」並按「朗讀」。裝置需有可離線使用的中文語音。
5. 「外觀」提供跟隨系統、淺色、深色，選擇會保存在手機。
6. 如需檢查 BLE，按「掃描 10 秒」，選取已辨識的音箱以查看 GATT 服務。

## 開發

需要 JDK 17 和 Android SDK 35。可用 Android Studio 開啟專案，或在設定 Android SDK 後執行以下指令。

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Android 版最低支援 Android 7.0。瀏覽器雛型仍可直接以瀏覽器開啟 `index.html`。

[規格與改造範圍](docs/scope.md)

[開發里程碑](docs/roadmap.md)
