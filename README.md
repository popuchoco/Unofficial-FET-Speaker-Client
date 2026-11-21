# Unofficial FET Speaker Client

支援小狐狸與小愛講的獨立 Android 用戶端。App 在使用者裝置上執行，無須伺服器或遠傳會員帳號。AI 對話及 KKBOX 播放交由各家官方服務處理。

## 使用 Android 預覽版

1. 在「我的音箱」開啟 Android 藍牙設定，配對小狐狸或小愛講並選為媒體輸出。
2. 回到首頁查看 Android 提供的藍牙媒體輸出，以及語音助理狀態。
3. 從「服務設定」到 ChatGPT、Gemini 或 Claude 官方頁面登入。KKBOX 也由官方入口開啟。
4. 如需測試，展開「測試工具」。可測試離線問答、音箱播放、媒體按鍵及 BLE。離線問答支援時間、日期、農曆、四則與單位換算。

語音助理的產品目標是由音箱喚醒並聽取問題，再由音箱播放回答。預覽版的手動收音和播放只用於獨立驗證音訊。音箱喚醒、音箱收音與自動回覆仍待驗證和整合。
## 開發

需要 JDK 17 和 Android SDK 35。可用 Android Studio 開啟專案，或在設定 Android SDK 後執行以下指令。

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Android 版最低支援 Android 7.0。瀏覽器雛型仍可直接以瀏覽器開啟 `index.html`。

[規格與改造範圍](docs/scope.md)

[開發里程碑](docs/roadmap.md)

## 資料與隱私

- 預設不連線任何雲端服務。
- 不需要位置權限，也不讀取或保存 GPS 座標。
- 已保存的上次裝置資訊位於 App 私有設定中，目前只儲存裝置名稱。
- 匯出內容不包含裝置位址。目前版本未提供匯出功能。
- Repository 不應包含私人裝置資料、憑證或使用者匯出內容。

## License

本專案採用 [MIT](LICENSE)。
