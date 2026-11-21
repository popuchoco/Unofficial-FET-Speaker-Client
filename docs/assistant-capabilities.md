# 語音助理功能重建規格

## 共用流程

音箱喚醒或按鍵事件 → 收音 → 語音辨識 → 判斷意圖並補問必要資訊 → 執行指定功能 → 音箱播報或播放。先實作可在手機本機執行的功能，並把語音觸發、資料來源和聲音輸出獨立處理。可用文字測資先驗證意圖和結果，整段流程仍須以小狐狸與小愛講實機驗收。

一般問答可以由 AI 處理。時間、行情、換算、鬧鐘和叫車交由各自的功能執行；有副作用的動作須用執行結果決定播報內容。原 App 的 `DataSyncApi` 對鬧鐘、提醒與語音留言使用帳戶 Token，TaxiGo 也走原服務的帳戶授權和地址管理。新版採手機本機或當前供應商官方入口，無須遠傳會員與專案後端。

## AI 服務授權評估

截至 2026-11-22，OpenAI 已提供 Sign in with ChatGPT 的開源用戶端流程：使用者在官方瀏覽器登入並同意 ChatGPT 方案用量後，App 可用 PKCE 與 `127.0.0.1` 本機回呼取得 Responses API 授權，不需 API 金鑰、用戶端密鑰或專案伺服器。符合資格的 ChatGPT Plus／Pro 帳戶可使用自己的方案額度。愛狐說已加入 Android 原生登入、模型選擇、授權資料的 Android Keystore 加密保存，以及按下送出後才發出的串流問答；請求設定 `store:false`，只有收到完整回答才交由手機 TTS 播放。最近八輪對話脈絡只暫存記憶體，不寫入裝置，使用者可隨時中斷連結。登入回呼只在本機 `127.0.0.1` 短暫監聽，不是專案伺服器。帳戶資格、授權與藍牙音箱實際播音仍待集中實機驗收。

Gemini API 使用 Google AI Studio／Cloud 專案 API 金鑰；Google 官方明確建議正式環境不要把金鑰放在手機 App，應由後端代送。Claude API 使用 Claude Console 的 API 金鑰，官方文件未提供把一般 Claude 帳戶登入授權給第三方手機 App 的流程。由於愛狐說不設專案後端，也不代管供應商金鑰，Gemini 與 Claude 維持官方頁面入口；若官方 App 或網站提供系統分享，使用者可將回答分享給愛狐說，由本機 TTS 朗讀。分享文字只在裝置上處理，不會送回 AI 服務或保存。這項交接不會把兩家服務變成自動語音問答。

## 問資訊

| 功能 | 資料與接法 | 語音回覆與驗收 |
| --- | --- | --- |
| 台灣天氣 | 已串中央氣象署各縣市鄉鎮一週預報；使用者在裝置設定個人授權碼，並以 Android Keystore 加密保存 | 使用預設鄉鎮或語句中的完整縣市與區名；支援今天目前時段、明天白天、後天白天，依氣象署摘要播報。正式授權連線待集中驗收 |
| 空氣品質 | 已接環境部 AQI 資料集 `AQX_P_432`。由使用者輸入個人授權碼，以 Android Keystore 加密保存；正式授權連線留待集中驗收 | 直接指定測站，或依縣市列出可選站點；報測站、AQI、等級和發布時間。缺值及超過三小時的資料皆會標明 |
| 台股 | 已用證交所與櫃買中心 OpenAPI 接上市、上櫃盤後行情；倚天股市原授權與即時行情另外驗證 | 支援公司名稱、代號。報最近交易日、收盤價與漲跌，標明「收盤」；即時價格需另有授權來源 |
| 維基百科 | 已接繁中維基百科 REST 搜尋與條目摘要，依規範送出 User-Agent；僅在明確百科指令時查詢 | 找到多個同名詞條時先補問；音箱播最合適詞條的短摘要，App 顯示來源連結 |
| 直播新聞 | 已支援公視新聞影音與官方直播頁面交接，其他台別或授權音訊串流另評估 | 「播放公視新聞」啟動官方播放器；若公開介面只有網頁入口，標示為開啟官方頁面，播放中斷則說明 |
| 匯率換算 | 使用臺灣土地銀行官方 CSV。臺灣銀行官方頁面可查詢牌告，但其下載端點在直接連線測試中回傳瀏覽器驗證頁，故目前採用土地銀行公開報價 | 提供新臺幣與外幣的參考換算，預設即期，說「現金」可改用現金牌告。語音及結果頁說明資料來源、買賣方向、參考性與查詢時間；CSV 未提供牌告時間 |
| 翻譯 | 採 Naver Cloud Papago Translation 文字 API，使用者自行申請並輸入 Client ID／Secret，App 直接連線，不設專案伺服器；Secret 以 Android Keystore 加密保存。依字數收費，原文只在明確翻譯時送出。**不採用 Google ML Kit 翻譯：官方支援清單中的中文目標碼 `zh` 對應簡體中文，沒有繁體中文目標 `zh-TW`，因此不符合本 App 的繁體中文翻譯需求。** | 「把你好翻成日文」使用自動原文偵測，結果頁標示 Papago 並連回官方頁；音箱播放手機已安裝的目標語言離線語音，未安裝時保留譯文。Naver Cloud 表示 API 不保存原文或用於訓練 |

## 生活幫手

| 功能 | 實作規格 | 語音回覆與驗收 |
| --- | --- | --- |
| 時間、日期、萬年曆 | 手機時區與時間、本機 Gregorian／ICU 農曆日曆 | 支援今天日期、星期、農曆、指定日期；測試跨日、閏年、農曆閏月 |
| 計算機 | 四則運算與括號；以 BigDecimal 計算並定義除法精度 | 繁中數字轉換、負數、優先順序、除零；語意不明時補問 |
| 單位換算 | 本機單位表，先做長度、重量、容量、溫度。臺灣斤、兩、坪、公制各自定義 | 核對量綱；例如台斤與其他地區的斤有歧義時補問 |
| 鬧鐘、倒數 | Android `AlarmClock.ACTION_SET_ALARM` 和 `ACTION_SET_TIMER` 交給手機時鐘 | 要求時間或秒數、釐清早晚。測試各廠牌時鐘是否受理；單純開啟時鐘介面時播報「已開啟設定」 |
| 提醒 | 手機本機保存待辦，支援相對時間與今天、明天、後天指定時段；通知由 Android 排程。精準提醒依裝置權限與排程能力，重啟後需恢復 | 明確覆誦事項、日期、時間；驗收新增、取消、重啟、權限拒絕及通知到期。音箱斷線時透過手機通知 |
| 家庭留言機 | 先做同手機保存與播放語音便條，錄音檔私有、可刪除；跨手機與跨音箱分享另評估使用者可控制的同步方式 | 錄音需明確啟動，結束後回覆已儲存。驗收列出、播放、刪除和多筆重名留言 |
| TaxiGo 叫車 | 已加入 LINE GO 官方叫車頁交接。比對可用的官方合作介面，確認可送單及查狀態後才納入語音直接叫車 | 首版開啟 LINE GO 並由使用者確認上車地點和下單。取得訂單結果後才說叫車成功 |

## 音樂服務

- KKBOX 的「開啟 KKBOX」語音指令會交給官方服務入口，使用者自行登入、選歌並播放，Android 再將系統音訊送往已配對音箱。App 不宣稱已點播歌曲；曲庫搜尋或直接播放權限須先取得 KKBOX 授權。
- Spotify 的「開啟 Spotify」語音指令會交給官方服務入口，使用者自行登入、選歌並播放，Android 再將系統音訊送往已配對音箱。Spotify 開發者政策不允許第三方語音助理控制 Spotify，因此不以 Web API 或 App Remote 實作語音點歌、播放控制或串流。

## 提交與驗收順序

1. 本機問答核心。首頁已提供繁中離線語音與文字輸入；本機支援時間、日期、農曆、計算與單位換算，其他一般問題可送至已連結的 ChatGPT，完成後由手機 TTS 播放。時間、國曆、運算與換算有 JVM 測資，農曆、語音辨識及藍牙播放待裝置驗收。
2. 手機動作。鬧鐘與倒數已接 Android 時鐘，支援繁中與二十四小時制，開啟時鐘後由使用者確認。指定日期鬧鐘仍在時鐘介面設定，實機相容性待測。本機提醒已支援相對時間與今天、明天、後天指定時段，使用 Android 通知，支援查看、取消和重啟恢復。通知可能受省電影響而延後。家庭留言已加入錄製、儲存、播放和刪除，音檔留在 App 私有目錄。提醒排程及留言錄播的實機驗收仍待完成。
3. 台灣資料。串天氣、AQI、台股盤後、匯率與維基百科。回應僅快取於 App 記憶體並按資料類型設定有效時間，不寫入磁碟；來源請求網址以 SHA-256 摘要作為快取鍵，避免保存 API 授權碼。各資料接法驗證欄位改動、無資料、過期、網路失敗與來源時間。
4. 媒體和外部服務。App 可用語音明確開啟 KKBOX、Spotify、公視新聞與 LINE GO 官方入口；選歌、實際播放及叫車由使用者在官方服務完成。Papago 由裝置直接呼叫使用者自行設定的 API 憑證。ChatGPT 已接官方開源用戶端授權與 Responses API；Gemini 與 Claude 暫保留官方頁面入口。尚待集中驗收各服務安裝、登入和音訊轉送情境；不得把開啟服務說成已播放或已叫車。
5. 音箱端驗收。把確認可用的喚醒、錄音與播放接入上述功能，以兩款音箱逐項測試。成功條件是正常問答無須打開 App 或複製貼上；需外部確認的動作要清楚交接。

## 來源

- [中央氣象署開發指南](https://opendata.cwa.gov.tw/devManual/description)
- [環境部開放資料 API](https://data.moenv.gov.tw/paradigm)
- [臺灣證交所 OpenAPI](https://openapi.twse.com.tw/) · [櫃買中心 OpenAPI](https://www.tpex.org.tw/openapi/)
- [MediaWiki REST API 使用規範](https://www.mediawiki.org/wiki/API:REST_API/Policies)
- [公視官方直播資訊](https://www.pts.org.tw/zh_tw/pr/station/)
- [臺灣土地銀行牌告匯率與 CSV](https://rate.landbank.com.tw/zh-TW/Foreign/)
- [Papago Translation 文字 API](https://api.ncloud-docs.com/docs/en/ai-naver-papagonmt-translation)
- [Papago API 費率與使用限制](https://guide.ncloud-docs.com/docs/en/papagotranslation-spec)
- [Papago 翻譯結果品牌標示](https://guide.ncloud-docs.com/docs/en/naveropenapiv3-translation-copyright)
- [Google ML Kit 翻譯支援語言](https://developers.google.com/ml-kit/language/translation/translation-language-support?hl=zh-tw)（目前列出中文 `zh`，未列繁體中文目標，因此不採用）
- [Android AlarmClock](https://developer.android.com/reference/android/provider/AlarmClock)
- [LINE GO 官方服務](https://www.linego.me/about_us)
- [ML Kit 裝置端翻譯使用規範](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Spotify 開發者政策](https://developer.spotify.com/policy)
- [Sign in with ChatGPT 開源用戶端快速開始](https://developers.openai.com/siwc/quickstart)
- [Sign in with ChatGPT 本機授權流程](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Sign in with ChatGPT Responses API 使用限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Gemini API 金鑰與安全建議](https://aistudio.google.com/docs/api-key)
- [Claude API 驗證方式](https://platform.claude.com/docs/en/manage-claude/authentication)
