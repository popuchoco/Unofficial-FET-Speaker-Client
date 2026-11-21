package community.speaker.client;

import android.Manifest;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.AlarmClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

@SuppressLint("SetTextI18n") // The app currently provides a single Traditional Chinese interface.
public final class MainActivity extends AppCompatActivity {
    private static final int BLE_PERMISSIONS = 42;
    private static final int MICROPHONE_PERMISSION = 43;
    private static final int REMINDER_NOTIFICATIONS = 44;
    private static final int MAILBOX_MICROPHONE_PERMISSION = 45;
    private static final String LAST_DEVICE_NAME = "last_device_name";
    private static final String PAPAGO_CLIENT_ID = "papago_client_id";
    private static final String WEATHER_PLACE = "weather_place";
    private static final String[] APPEARANCES = { "跟隨系統", "淺色", "深色" };
    private static final int[] NIGHT_MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
    };
    private int appearance;
    private LinearLayout section;
    private LinearLayout content;
    private LinearLayout devices;
    private EditText reply;
    private EditText question;
    private VoiceInput voiceInput;
    private TextView voiceStatus;
    private String pendingSharedRead;
    private TextView chatGptStatus;
    private Button chatGptConnectionButton;
    private Button replaySpeechButton;
    private ChatGptProvider chatGptProvider;
    private ServiceCredentialStore serviceCredentials;
    private String serviceCredentialError;
    private final ChatGptConversation chatGptConversation = new ChatGptConversation();
    private final SpeechReplay speechReplay = new SpeechReplay();
    private final java.util.concurrent.ExecutorService chatGptExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> chatGptTask;
    private volatile ChatGptProvider.AuthorizationRequest activeChatGptAuthorization;
    private volatile boolean chatGptLoginCancelled;
    private Button lookupSource;
    private String lookupSourceUrl;
    private final java.util.concurrent.ExecutorService lookupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> lookupTask;
    private int lookupGeneration;
    private ReminderRequest pendingReminder;
    private VoiceMessages voiceMessages;
    private androidx.appcompat.app.AlertDialog mailboxDialog;
    private TextView mailboxStatus;
    private LinearLayout mailboxList;
    private TextView speechStatus;
    private TextView bluetoothStatus;
    private TextView lastDevice;
    private Button speakButton;
    private Button appearanceButton;
    private TextView audioStatus;
    private AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener speechFocusListener = this::onSpeechAudioFocusChange;
    private AudioFocusRequest speechFocusRequest;
    private boolean speechFocusActive;
    private final AudioDeviceCallback audioDevices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { updateAudioStatus(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { updateAudioStatus(); }
    };
    private TextToSpeech tts;
    private Voice chineseVoice;
    private boolean translationVoiceActive;
    private BleInspector ble;
    private MediaKeyDiagnostic mediaKeys;
    private boolean speechReady;
    private int utteranceNumber;
    private String activeUtterance;
    private final Set<String> pendingSpeechUtterances = new HashSet<>();

    @Override
    protected void onCreate(Bundle state) {
        appearance = getPreferences(MODE_PRIVATE).getInt("appearance", 0);
        getDelegate().setLocalNightMode(NIGHT_MODES[appearance]);
        super.onCreate(state);
        serviceCredentials = new ServiceCredentialStore(this);
        try {
            serviceCredentials.migrate(getPreferences(MODE_PRIVATE));
        } catch (IOException error) {
            serviceCredentialError = "服務憑證安全遷移失敗，請重新設定相關服務憑證。";
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        content.setBackgroundColor(MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface));
        section = content;
        scroll.addView(content);
        content.setFocusableInTouchMode(true);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, windowInsets) -> {
                androidx.core.graphics.Insets bars = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                                | androidx.core.view.WindowInsetsCompat.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return windowInsets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(scroll);
        }
        content.requestFocus();
        chatGptProvider = new ChatGptProvider(this);
        if (Build.VERSION.SDK_INT >= 27) {
            getWindow().setNavigationBarColor(getColor(R.color.speaker_surface));
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), scroll)
                    .setAppearanceLightNavigationBars(getResources().getBoolean(R.bool.speaker_light_bars));
        }

        label("愛狐說", 28);
        startSection("我的音箱");
        label("小狐狸與小愛講", 15);
        audioStatus = label("", 15);
        audioStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        addButton(section, "設定藍牙音箱", () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
                com.google.android.material.R.attr.materialButtonStyle);
        audioManager = getSystemService(AudioManager.class);
        updateAudioStatus();

        startSection("語音助理");
        label("用手機麥克風提問，回答會由本機中文語音播放至目前的藍牙音箱。音箱喚醒與按鍵觸發仍待實機接入。", 15);
        question = addTextInput("問愛狐說");
        voiceStatus = label("準備就緒。", 15);
        voiceStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        voiceInput = new VoiceInput(this, voiceStatus::setText, transcript -> {
            question.setText(transcript);
            answerLocalQuestion();
        });
        addButton(section, "開始說話", this::requestVoiceInput,
                com.google.android.material.R.attr.materialButtonStyle);
        addButton(section, "送出問題", this::answerLocalQuestion);
        replaySpeechButton = addButton(section, "重播最近語音", this::replayRecentSpeech,
                androidx.appcompat.R.attr.borderlessButtonStyle);
        replaySpeechButton.setEnabled(false);
        chatGptStatus = label("", 15);
        chatGptStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        chatGptConnectionButton = addButton(section, "連結 ChatGPT", this::manageChatGpt);
        updateChatGptStatus();

        addButton(section, "查看本機提醒", this::showReminders);
        voiceMessages = new VoiceMessages(this);
        addButton(section, "家庭留言", this::showMailbox);

        startSection("服務設定");
        label("在官方頁面自行登入。", 15);
        addLink("開啟 ChatGPT", "https://chatgpt.com/");
        addLink("開啟 Gemini", "https://gemini.google.com/app");
        addLink("開啟 Claude", "https://claude.ai/");
        addLink("開啟 KKBOX", "https://www.kkbox.com/tw/");
        addLink("開啟 Spotify", "https://open.spotify.com/");
        addLink("公視新聞影音", ServiceHandoff.PUBLIC_NEWS.url);
        addLink("LINE GO 叫車", ServiceHandoff.TAXI.url);
        addButton(section, "設定空氣品質資料來源", this::configureAirQuality);
        addButton(section, "設定天氣資料來源", this::configureWeather);
        addButton(section, "設定 Papago 翻譯 API", this::configurePapago);

        appearanceButton = addButton(content, "外觀 · " + APPEARANCES[appearance], this::chooseAppearance);
        LinearLayout diagnostics = new LinearLayout(this);
        diagnostics.setOrientation(LinearLayout.VERTICAL);
        diagnostics.setVisibility(View.GONE);
        MaterialButton diagnosticToggle = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        diagnosticToggle.setText("開啟測試工具");
        diagnosticToggle.setMinHeight(dp(48));
        diagnosticToggle.setOnClickListener(view -> {
            boolean show = diagnostics.getVisibility() != View.VISIBLE;
            diagnostics.setVisibility(show ? View.VISIBLE : View.GONE);
            diagnosticToggle.setText(show ? "收起測試工具" : "開啟測試工具");
            if (!show) stopDiagnostics();
        });
        content.addView(diagnosticToggle);
        content.addView(diagnostics);

        buildDiagnostics(diagnostics);
        tts = new TextToSpeech(this, result -> new Handler(Looper.getMainLooper())
                .post(() -> configureSpeech(result)));
        handleSharedText(getIntent());
    }

    private void buildDiagnostics(LinearLayout diagnostics) {
        startSection(diagnostics, "資料來源");
        lookupSource = addButton(section, "開啟資料來源", () -> {
            if (lookupSourceUrl != null) open(new Intent(Intent.ACTION_VIEW, Uri.parse(lookupSourceUrl)));
        }, androidx.appcompat.R.attr.borderlessButtonStyle);
        lookupSource.setVisibility(View.GONE);

        startSection(diagnostics, "播放測試");
        reply = addTextInput("輸入測試文字");
        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof String[]) {
            String[] drafts = (String[]) retained;
            reply.setText(drafts[0]);
            question.setText(drafts[1]);
        }
        speakButton = addButton(section, "播放測試語音", this::speak, com.google.android.material.R.attr.materialButtonStyle);
        speakButton.setEnabled(false);
        addButton(section, "停止朗讀", this::stopSpeech, androidx.appcompat.R.attr.borderlessButtonStyle);
        speechStatus = label("正在載入本機語音…", 15);
        speechStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);

        startSection(diagnostics, "音箱按鍵診斷");
        label("先暫停其他 App 的媒體播放，再測試音箱上的播放或暫停按鍵。", 15);
        TextView keyStatus = label("", 15);
        keyStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        mediaKeys = new MediaKeyDiagnostic(this, keyStatus::setText);
        addButton(section, "測試媒體按鍵", mediaKeys::start);
        addButton(section, "結束測試", () -> mediaKeys.stop("已結束按鍵測試。"),
                androidx.appcompat.R.attr.borderlessButtonStyle);

        startSection(diagnostics, "BLE 診斷");
        label("掃描附近裝置。選取結果可查看該裝置提供的 GATT 服務。", 15);
        addButton(section, "掃描 10 秒", this::requestScan);
        lastDevice = label("", 15);
        updateLastDevice();
        bluetoothStatus = label("", 15);
        bluetoothStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        devices = new LinearLayout(this);
        devices.setOrientation(LinearLayout.VERTICAL);
        section.addView(devices);
        ble = new BleInspector(this, bluetoothStatus::setText, this::showDevice);

    }

    private void configureSpeech(int result) {
        if (isDestroyed() || tts == null) return;
        Set<Voice> voices = result == TextToSpeech.SUCCESS ? tts.getVoices() : null;
        Voice chinese = voices == null ? null : voices.stream()
                .filter(voice -> !voice.isNetworkConnectionRequired())
                .filter(voice -> !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                .filter(voice -> voice.getLocale().getLanguage().equals(Locale.CHINESE.getLanguage()))
                .min(Comparator.comparing(voice -> !voice.getLocale().getCountry().equals("TW")))
                .orElse(null);
        if (chinese == null || tts.setVoice(chinese) != TextToSpeech.SUCCESS) {
            speechStatus.setText("請在 Android 語音設定安裝可離線使用的中文語音。");
            return;
        }
        chineseVoice = chinese;
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { finishSpeech(id, "朗讀完成。"); }
            @Override public void onError(String id) { failSpeech(id); }
        });
        speechReady = true;
        speakButton.setEnabled(true);
        speechStatus.setText("本機中文語音就緒。");
        if (pendingSharedRead != null) {
            String text = pendingSharedRead;
            pendingSharedRead = null;
            voiceStatus.setText("正在朗讀分享的回答。");
            speakText(text);
        }
    }

    private void handleSharedText(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String input = shared == null ? null : shared.toString();
        setIntent(new Intent(this, MainActivity.class));
        String text;
        try {
            text = SharedTextRequest.normalize(input);
        } catch (IllegalArgumentException tooLong) {
            voiceStatus.setText("分享內容太長，請分段後再分享。");
            return;
        }
        if (text == null) {
            voiceStatus.setText("沒有收到可朗讀的文字。");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("朗讀分享的回答")
                .setMessage(text)
                .setPositiveButton("朗讀", (dialog, which) -> readSharedText(text))
                .setNegativeButton("關閉", null)
                .show();
    }

    private void readSharedText(String text) {
        cancelLookup();
        stopSpeech();
        if (!speechReady) {
            pendingSharedRead = text;
            voiceStatus.setText("本機語音載入完成後會開始朗讀。");
            return;
        }
        voiceStatus.setText("正在朗讀分享的回答。");
        speakText(text);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
    }

    private void speak() {
        if (!speechReady) return;
        String text = reply.getText().toString().trim();
        if (text.isEmpty()) {
            speechStatus.setText("請先貼上要朗讀的文字。");
            return;
        }
        speakText(text);
    }

    private void answerLocalQuestion() {
        cancelLookup();
        String input = question.getText().toString().trim();
        if (input.isEmpty()) {
            voiceStatus.setText("請先說出或輸入問題。");
            return;
        }
        TranslationRequest translation = TranslationRequest.parse(input);
        if (translation != null) {
            if (translation.error != null) {
                speakAssistantStatus(translation.error);
            } else {
                translate(translation);
            }
            return;
        }
        String wikiTopic = WikipediaLookup.topic(input);
        if (wikiTopic != null) {
            lookupWikipedia(wikiTopic);
            return;
        }
        ReminderRequest reminder = ReminderRequest.parse(input);
        if (reminder != null) {
            requestReminder(reminder);
            return;
        }
        ServiceHandoff service = ServiceHandoff.parse(input);
        if (service != null) {
            boolean opened = open(new Intent(Intent.ACTION_VIEW, Uri.parse(service.url)));
            speakAssistantStatus(opened ? service.confirmation : "無法開啟官方服務。");
            return;
        }
        WeatherLookup.Request weather = WeatherLookup.parse(input);
        if (weather != null) {
            lookupWeather(weather);
            return;
        }
        String airPlace = AirQualityLookup.place(input);
        if (airPlace != null) {
            String key = savedCredential(ServiceCredentialStore.MOENV_API_KEY);
            if (airPlace.isEmpty() || airPlace.length() > 40) {
                speakAssistantStatus("請指定測站，例如查空氣品質松山。");
            } else if (key.isEmpty()) {
                speakAssistantStatus(credentialErrorOr("請先在服務設定輸入環境部資料授權碼。"));
            } else {
                startLookup("正在查詢空氣品質…", "無法取得空氣品質，請確認授權碼或稍後再試。",
                        () -> AirQualityLookup.search(airPlace, key));
            }
            return;
        }
        ExchangeLookup.Request exchange = ExchangeLookup.parse(input);
        if (exchange != null) {
            startLookup("正在查詢臺灣土地銀行牌告匯率…",
                    "無法取得臺灣土地銀行牌告匯率，請稍後再試；可開啟資料來源查看官方報價。",
                    ExchangeLookup.SOURCE,
                    () -> ExchangeLookup.search(exchange));
            return;
        }
        String stock = StockLookup.symbol(input);
        if (stock != null) {
            if (stock.isEmpty() || stock.length() > 40) {
                voiceStatus.setText("請說出股票代號或名稱，例如查台股2330。");
            } else {
                startLookup("正在查詢台股收盤資料…", "無法取得台股收盤資料，請稍後再試。",
                        () -> StockLookup.search(stock));
            }
            return;
        }
        ClockRequest clock = ClockRequest.parse(input);
        if (clock != null) {
            handleClockRequest(clock);
            return;
        }
        String answer = LocalAnswer.reply(input, java.util.Calendar.getInstance());
        if (answer == null) {
            if (chatGptProvider.isConnected() && !chatGptProvider.selectedModelSlug().isEmpty()) {
                String model = chatGptProvider.selectedModelSlug();
                startLookup("正在詢問 ChatGPT…", "ChatGPT 暫時無法回答，請檢查連線或稍後再試。",
                        () -> new LookupAnswer(chatGptConversation.ask(input,
                                messages -> chatGptProvider.respond(model, messages)), null));
            } else if (chatGptProvider.isConnected()) {
                speakAssistantStatus("ChatGPT 已連結，請先選擇可用模型。");
            } else {
                speakAssistantStatus("這個問題目前無法離線回答。你可以在此連結 ChatGPT，或改說本機支援的指令。");
            }
            return;
        }
        voiceStatus.setText(answer);
        if (speechReady) speakText(answer);
    }

    private void updateChatGptStatus() {
        if (chatGptStatus == null || chatGptConnectionButton == null || chatGptProvider == null) return;
        if (chatGptProvider.isConnected()) {
            String model = chatGptProvider.selectedModelName();
            chatGptStatus.setText(model.isEmpty()
                    ? "ChatGPT 已連結，尚未選擇模型。未由本機處理的問題會送至 ChatGPT。"
                    : "ChatGPT 已連結 · " + model + "。未由本機處理的問題會送至 ChatGPT。");
            chatGptConnectionButton.setText("管理 ChatGPT 連結");
        } else {
            chatGptStatus.setText("只有送出問題後才會連線。使用者可用自己的 ChatGPT 帳戶授權，不需 API 金鑰或專案伺服器。");
            chatGptConnectionButton.setText("連結 ChatGPT");
        }
    }

    private void manageChatGpt() {
        if (!chatGptProvider.isConnected()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("連結 ChatGPT")
                    .setMessage("將在官方登入頁面由你自行登入並授權愛狐說使用 ChatGPT 方案用量。問題會由此裝置直接送至 OpenAI；對話脈絡只暫存於記憶體，不寫入裝置。")
                    .setPositiveButton("前往登入", (dialog, which) -> connectChatGpt())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("ChatGPT 設定")
                .setMessage("目前模型：" + (chatGptProvider.selectedModelName().isEmpty()
                        ? "尚未選擇" : chatGptProvider.selectedModelName())
                        + "\n目前保留 " + chatGptConversation.turnCount() + " 輪對話（最多八輪），只存在記憶體；關閉 App 後清除。")
                .setItems(new String[] { "選擇模型", "清除對話脈絡", "中斷連結" }, (dialog, choice) -> {
                    if (choice == 0) loadChatGptModels(true);
                    else if (choice == 1) confirmClearChatGptConversation();
                    else confirmDisconnectChatGpt();
                })
                .setNegativeButton("關閉", null)
                .show();
    }

    private void confirmClearChatGptConversation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清除對話脈絡？")
                .setMessage("只會清除愛狐說記憶體中的短期脈絡，不會送出網路請求，也不會中斷 ChatGPT 連結。")
                .setPositiveButton("清除", (confirmation, button) -> {
                    chatGptConversation.clear();
                    speakAssistantStatus("已清除 ChatGPT 對話脈絡。");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDisconnectChatGpt() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("中斷 ChatGPT 連結？")
                .setMessage("移除此手機上的 ChatGPT 授權資料，並清除暫存的對話脈絡。")
                .setPositiveButton("中斷連結", (confirmation, button) -> {
                    cancelLookup();
                    chatGptProvider.clearConnection();
                    chatGptConversation.clear();
                    updateChatGptStatus();
                    speakAssistantStatus("已移除這部手機上的 ChatGPT 連結。");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void connectChatGpt() {
        if (chatGptTask != null && !chatGptTask.isDone()) {
            speakAssistantStatus("ChatGPT 登入流程已在進行中，請完成瀏覽器授權後返回愛狐說。");
            return;
        }
        chatGptLoginCancelled = false;
        chatGptStatus.setText("正在準備 ChatGPT 官方登入…");
        chatGptTask = chatGptExecutor.submit(() -> {
            ChatGptProvider.AuthorizationRequest attempt = null;
            try {
                attempt = chatGptProvider.beginAuthorization();
                activeChatGptAuthorization = attempt;
                ChatGptProvider.AuthorizationRequest request = attempt;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        request.close();
                        return;
                    }
                    boolean opened = open(new Intent(Intent.ACTION_VIEW, Uri.parse(request.authorizationUrl)));
                    if (!opened) {
                        chatGptLoginCancelled = true;
                        request.close();
                        chatGptStatus.setText("無法開啟 ChatGPT 官方登入頁面。");
                    } else {
                        chatGptStatus.setText("請在瀏覽器完成登入與授權，再返回愛狐說。");
                    }
                });
                chatGptProvider.finishAuthorization(attempt);
                activeChatGptAuthorization = null;
                java.util.List<ChatGptProvider.Model> models = chatGptProvider.listModels();
                if (chatGptProvider.selectedModelSlug().isEmpty()) chatGptProvider.selectModel(models.get(0));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    updateChatGptStatus();
                    speakAssistantStatus("ChatGPT 已連結，可以直接說出問題。");
                });
            } catch (java.io.IOException error) {
                if (attempt != null) attempt.close();
                activeChatGptAuthorization = null;
                if (!chatGptLoginCancelled && !Thread.currentThread().isInterrupted()) runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (chatGptProvider.isConnected()) {
                        updateChatGptStatus();
                        chatGptStatus.setText((error.getMessage() == null
                                ? "無法載入 ChatGPT 設定。" : error.getMessage()) + " 連結仍保留，可稍後重試。");
                    } else {
                        chatGptStatus.setText(error.getMessage() == null
                                ? "ChatGPT 連結失敗，請稍後重試。" : error.getMessage());
                        chatGptConnectionButton.setText("重新連結 ChatGPT");
                    }
                });
            }
        });
    }

    private void loadChatGptModels(boolean showPicker) {
        chatGptStatus.setText("正在讀取 ChatGPT 可用模型…");
        chatGptExecutor.submit(() -> {
            try {
                java.util.List<ChatGptProvider.Model> models = chatGptProvider.listModels();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    updateChatGptStatus();
                    if (showPicker) {
                        ChatGptProvider.Model[] options = models.toArray(new ChatGptProvider.Model[0]);
                        CharSequence[] labels = new CharSequence[options.length];
                        int selected = 0;
                        String current = chatGptProvider.selectedModelSlug();
                        for (int i = 0; i < options.length; i++) {
                            labels[i] = options[i].name;
                            if (options[i].slug.equals(current)) selected = i;
                        }
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("選擇 ChatGPT 模型")
                                .setSingleChoiceItems(labels, selected, (dialog, choice) -> {
                                    try {
                                        chatGptProvider.selectModel(options[choice]);
                                        updateChatGptStatus();
                                    } catch (java.io.IOException error) {
                                        chatGptStatus.setText("無法儲存模型選擇。");
                                    }
                                    dialog.dismiss();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            } catch (java.io.IOException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    chatGptStatus.setText(error.getMessage() == null
                            ? "無法讀取 ChatGPT 模型。" : error.getMessage());
                });
            }
        });
    }

    private void lookupWeather(WeatherLookup.Request request) {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String place = request.place.isEmpty() ? settings.getString(WEATHER_PLACE, "") : request.place;
        String key = savedCredential(ServiceCredentialStore.CWA_API_KEY);
        if (place.length() > 40 || WeatherLookup.dataset(place) == null) {
            speakAssistantStatus("請說出完整縣市和鄉鎮市區，例如查臺北市信義區明天天氣。");
        } else if (key.isEmpty()) {
            speakAssistantStatus(credentialErrorOr("請先在服務設定輸入中央氣象署授權碼。"));
        } else {
            startLookup("正在查詢氣象署預報…", "無法取得天氣預報，請確認授權碼或稍後再試。",
                    () -> WeatherLookup.search(place, request.days, key));
        }
    }

    private void configureWeather() {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), 0, dp(24), 0);
        EditText key = new TextInputEditText(this);
        key.setHint("中央氣象署授權碼");
        key.setSingleLine(true);
        key.setSaveEnabled(false);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(savedCredential(ServiceCredentialStore.CWA_API_KEY));
        panel.addView(key);
        EditText place = new TextInputEditText(this);
        place.setSingleLine(true);
        place.setHint("預設鄉鎮，例如臺北市信義區");
        place.setText(settings.getString(WEATHER_PLACE, ""));
        panel.addView(place);
        new MaterialAlertDialogBuilder(this).setTitle("天氣資料來源")
                .setMessage("授權碼以 Android Keystore 加密保存在此手機，只有主動查詢才會送往氣象署。")
                .setView(panel)
                .setNeutralButton("開啟氣象署", (dialog, button) ->
                        open(new Intent(Intent.ACTION_VIEW, Uri.parse(WeatherLookup.SOURCE))))
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (dialog, button) -> {
                    saveCredential(ServiceCredentialStore.CWA_API_KEY, key.getText().toString());
                    settings.edit().putString(WEATHER_PLACE, place.getText().toString().trim()).apply();
                    announceCredentialSaveError();
                })
                .show();
    }

    private void configureAirQuality() {
        EditText input = new TextInputEditText(this);
        input.setHint("環境部授權碼");
        input.setSingleLine(true);
        input.setSaveEnabled(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(savedCredential(ServiceCredentialStore.MOENV_API_KEY));
        new MaterialAlertDialogBuilder(this)
                .setTitle("空氣品質資料來源")
                .setMessage("使用環境部開放資料平台核發的授權碼。授權碼以 Android Keystore 加密保存在此手機，只有主動查詢才會送往環境部。")
                .setView(input)
                .setNeutralButton("開啟環境部", (dialog, button) ->
                        open(new Intent(Intent.ACTION_VIEW, Uri.parse(AirQualityLookup.SOURCE))))
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (dialog, button) -> {
                    saveCredential(ServiceCredentialStore.MOENV_API_KEY, input.getText().toString());
                    announceCredentialSaveError();
                })
                .show();
    }

    private void configurePapago() {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), 0, dp(24), 0);
        EditText clientId = new TextInputEditText(this);
        clientId.setHint("Naver Cloud Client ID");
        clientId.setSingleLine(true);
        clientId.setSaveEnabled(false);
        clientId.setText(settings.getString(PAPAGO_CLIENT_ID, ""));
        panel.addView(clientId);
        EditText clientSecret = new TextInputEditText(this);
        clientSecret.setHint("Naver Cloud Client Secret");
        clientSecret.setSingleLine(true);
        clientSecret.setSaveEnabled(false);
        clientSecret.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        clientSecret.setText(savedCredential(ServiceCredentialStore.PAPAGO_CLIENT_SECRET));
        panel.addView(clientSecret);
        new MaterialAlertDialogBuilder(this).setTitle("Papago 翻譯 API")
                .setMessage("請在 Naver Cloud 建立 Papago Translation API 應用並輸入自己的 Client ID 與 Secret。Client ID 保存在 App 私有設定，Secret 以 Android Keystore 加密保存在此手機；僅在你明確要求翻譯時送往 Papago。服務依字數收費，請先在 Naver Cloud 設定用量上限。留空儲存可移除憑證。")
                .setView(panel)
                .setNeutralButton("官方 API 指引", (dialog, button) ->
                        open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://guide.ncloud-docs.com/docs/en/papagotranslation-procedure"))))
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (dialog, button) -> {
                    settings.edit().putString(PAPAGO_CLIENT_ID, clientId.getText().toString().trim()).apply();
                    saveCredential(ServiceCredentialStore.PAPAGO_CLIENT_SECRET, clientSecret.getText().toString());
                    announceCredentialSaveError();
                })
                .show();
    }

    private void translate(TranslationRequest request) {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String clientId = settings.getString(PAPAGO_CLIENT_ID, "");
        String clientSecret = savedCredential(ServiceCredentialStore.PAPAGO_CLIENT_SECRET);
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            speakAssistantStatus(credentialErrorOr("請先在服務設定輸入自己的 Papago API 憑證，再試一次翻譯。"));
            return;
        }
        startLookup("正在使用 Papago 翻譯…",
                "Papago 翻譯失敗，請確認網路、API 憑證或 Naver Cloud 用量上限。",
                PapagoLookup.SOURCE,
                () -> PapagoLookup.search(request, clientId, clientSecret));
    }

    private String savedCredential(String name) {
        try {
            return serviceCredentials.get(name);
        } catch (IOException error) {
            serviceCredentialError = "無法讀取已保存的服務憑證，請重新設定。";
            return "";
        }
    }

    private void saveCredential(String name, String value) {
        try {
            serviceCredentials.save(name, value);
            serviceCredentialError = null;
        } catch (IOException error) {
            serviceCredentialError = "無法安全保存服務憑證，請檢查手機儲存狀態後重試。";
        }
    }

    private String credentialErrorOr(String missingCredentialMessage) {
        return serviceCredentialError == null ? missingCredentialMessage : serviceCredentialError;
    }

    private void announceCredentialSaveError() {
        if (serviceCredentialError != null) speakAssistantStatus(serviceCredentialError);
    }

    private void lookupWikipedia(String topic) {
        if (topic.isEmpty() || topic.length() > 120) {
            voiceStatus.setText("請說出要查詢的詞條名稱，例如查維基百科台北101。");
            return;
        }
        startLookup("正在查詢維基百科…", "無法取得維基百科資料，請稍後再試。",
                () -> WikipediaLookup.search(topic));
    }

    private interface Lookup {
        LookupAnswer fetch() throws java.io.IOException, org.json.JSONException;
    }

    private void startLookup(String loading, String failure, Lookup lookup) {
        startLookup(loading, failure, null, lookup);
    }

    private void startLookup(String loading, String failure, String failureSource, Lookup lookup) {
        stopSpeech();
        int generation = lookupGeneration;
        voiceStatus.setText(loading);
        lookupTask = lookupExecutor.submit(() -> {
            LookupAnswer response;
            try {
                response = lookup.fetch();
            } catch (java.io.IOException | org.json.JSONException error) {
                response = new LookupAnswer(failure, failureSource);
            }
            LookupAnswer answer = response;
            runOnUiThread(() -> {
                if (lookupGeneration != generation || isDestroyed()) return;
                lookupTask = null;
                lookupSourceUrl = answer.source;
                lookupSource.setVisibility(lookupSourceUrl == null ? View.GONE : View.VISIBLE);
                voiceStatus.setText(answer.text);
                if (speechReady) {
                    if (answer.speechText != null && answer.speechLocale != null) {
                        speakTranslated(answer.speechText, answer.speechLocale);
                    } else {
                        speakText(answer.text);
                    }
                }
            });
        });
    }

    private void cancelLookup() {
        lookupGeneration++;
        if (lookupTask != null) {
            lookupTask.cancel(true);
            lookupTask = null;
            voiceStatus.setText("已取消查詢。");
        }
        lookupSourceUrl = null;
        if (lookupSource != null) lookupSource.setVisibility(View.GONE);
    }

    private void requestReminder(ReminderRequest request) {
        if (request.seconds == 0 && request.dueAtMillis == 0) {
            speakAssistantStatus("請說明提醒時間和事項，例如三分鐘後提醒我關火，或明天早上九點提醒我拿包裹。");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = request;
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, REMINDER_NOTIFICATIONS);
            return;
        }
        if (!ReminderReceiver.notificationsAvailable(this)) {
            speakAssistantStatus("請先在 Android 設定開啟愛狐說的通知。");
            return;
        }
        ReminderReceiver.Entry entry = ReminderReceiver.add(this, request);
        if (entry == null) {
            speakAssistantStatus("未能儲存提醒，請再試一次。");
            return;
        }
        String time = android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(entry.due));
        speakAssistantStatus("已安排 " + time + " 提醒你" + entry.message + "。通知可能因省電設定延後。");
    }

    private void speakAssistantStatus(String message) {
        voiceStatus.setText(message);
        if (speechReady) speakText(message);
    }

    private void showReminders() {
        java.util.List<ReminderReceiver.Entry> entries = ReminderReceiver.list(this);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("本機提醒").setNegativeButton("關閉", null);
        if (entries.isEmpty()) {
            dialog.setMessage("目前沒有待處理的提醒。");
        } else {
            java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
            String[] labels = new String[entries.size()];
            for (int index = 0; index < entries.size(); index++) {
                ReminderReceiver.Entry entry = entries.get(index);
                labels[index] = format.format(new java.util.Date(entry.due)) + "\n" + entry.message;
            }
            dialog.setItems(labels, (picker, index) -> {
                ReminderReceiver.Entry entry = entries.get(index);
                new MaterialAlertDialogBuilder(this).setTitle("取消提醒？").setMessage(entry.message)
                        .setNegativeButton("保留", null)
                        .setPositiveButton("取消提醒", (confirmation, button) -> {
                            if (!ReminderReceiver.cancel(this, entry)) {
                                android.widget.Toast.makeText(this, "無法取消提醒，請再試一次。",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                            showReminders();
                        }).show();
            });
        }
        dialog.show();
    }

    private void showMailbox() {
        stopDiagnostics();
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(12));
        scroll.addView(panel);
        mailboxStatus = new MaterialTextView(this);
        mailboxStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(mailboxStatus);
        addButton(panel, "錄製留言", this::requestMailboxRecording);
        addButton(panel, "儲存錄音", this::saveMailboxRecording);
        addButton(panel, "取消錄音", () -> {
            voiceMessages.cancel();
            mailboxStatus.setText("已取消錄音。");
        }, androidx.appcompat.R.attr.borderlessButtonStyle);
        addButton(panel, "停止播放", () -> {
            voiceMessages.stopPlayback();
            mailboxStatus.setText("已停止播放。");
        }, androidx.appcompat.R.attr.borderlessButtonStyle);
        mailboxList = new LinearLayout(this);
        mailboxList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(mailboxList);
        mailboxDialog = new MaterialAlertDialogBuilder(this).setTitle("家庭留言")
                .setView(scroll).setNegativeButton("關閉", null).create();
        mailboxDialog.setOnDismissListener(dialog -> {
            voiceMessages.cancel();
            voiceMessages.stopPlayback();
            mailboxDialog = null;
        });
        refreshMailbox();
        mailboxDialog.show();
    }

    private void requestMailboxRecording() {
        if (voiceMessages.isRecording()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, MAILBOX_MICROPHONE_PERMISSION);
            return;
        }
        voiceMessages.record(mailboxStatus::setText);
        if (voiceMessages.isRecording()) mailboxStatus.setText("錄音中。說完後按「儲存錄音」。");
    }

    private void saveMailboxRecording() {
        if (!voiceMessages.isRecording()) {
            mailboxStatus.setText("請先錄製留言。");
            return;
        }
        try {
            voiceMessages.save();
            refreshMailbox();
            mailboxStatus.setText("已儲存留言。");
        } catch (java.io.IOException error) {
            mailboxStatus.setText("無法儲存留言。請重新錄音。");
        }
    }

    private void refreshMailbox() {
        mailboxList.removeAllViews();
        try {
            java.text.DateFormat times = java.text.DateFormat.getDateTimeInstance();
            java.util.List<java.io.File> recordings = voiceMessages.list();
            if (recordings.isEmpty()) mailboxStatus.setText("目前沒有留言。");
            for (int index = 0; index < recordings.size(); index++) {
                java.io.File recording = recordings.get(index);
                String title = "留言 " + (index + 1) + " · "
                        + times.format(new java.util.Date(recording.lastModified()));
                addButton(mailboxList, title, () -> showMessageActions(recording, title));
            }
        } catch (java.io.IOException error) {
            mailboxStatus.setText("無法載入家庭留言。");
        }
    }

    private void showMessageActions(java.io.File recording, String title) {
        if (voiceMessages.isRecording()) {
            mailboxStatus.setText("請先儲存或取消此次錄音。");
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(title).setItems(
                new String[] { "播放留言", "刪除留言" }, (dialog, selected) -> {
                    if (selected == 0) {
                        stopSpeech();
                        mailboxStatus.setText("正在播放留言。");
                        voiceMessages.play(recording, mailboxStatus::setText);
                    } else {
                        new MaterialAlertDialogBuilder(this).setTitle("刪除這段留言？")
                                .setNegativeButton("保留", null)
                                .setPositiveButton("刪除", (confirmation, button) -> {
                                    if (voiceMessages.delete(recording)) {
                                        refreshMailbox();
                                        mailboxStatus.setText("已刪除留言。");
                                    } else {
                                        mailboxStatus.setText("無法刪除留言。");
                                    }
                                }).show();
                    }
                }).setNegativeButton("關閉", null).show();
    }

    private void handleClockRequest(ClockRequest request) {
        if (request.kind == ClockRequest.Kind.CLARIFICATION) {
            voiceStatus.setText(request.prompt);
            if (speechReady) speakText(request.prompt);
            return;
        }
        Intent intent;
        if (request.kind == ClockRequest.Kind.ALARM) {
            intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, request.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, request.minute);
        } else {
            intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, request.seconds);
        }
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        try {
            startActivity(intent);
            voiceStatus.setText("已開啟 Android 時鐘，請確認設定。");
        } catch (ActivityNotFoundException | SecurityException error) {
            voiceStatus.setText("這台裝置沒有可處理此指令的 Android 時鐘。");
        }
    }

    private void speakText(String text) {
        stopSpeech();
        restoreChineseVoice();
        enqueueSpeech(text, null);
    }

    private void speakTranslated(String text, String localeTag) {
        stopSpeech();
        java.util.Locale locale = java.util.Locale.forLanguageTag(localeTag);
        Set<Voice> voices = tts == null ? null : tts.getVoices();
        Voice target = voices == null ? null : voices.stream()
                .filter(voice -> !voice.isNetworkConnectionRequired())
                .filter(voice -> !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                .filter(voice -> voice.getLocale().getLanguage().equals(locale.getLanguage()))
                .min(Comparator.comparing(voice -> !voice.getLocale().getCountry().equals(locale.getCountry())))
                .orElse(null);
        if (target == null || tts.setVoice(target) != TextToSpeech.SUCCESS) {
            voiceStatus.append("\n此裝置沒有已安裝的離線目標語音，譯文保留在畫面。");
            speechStatus.setText("此裝置沒有該語言的離線語音。");
            restoreChineseVoice();
            return;
        }
        translationVoiceActive = true;
        enqueueSpeech(text, localeTag);
    }

    private void replayRecentSpeech() {
        SpeechReplay.Utterance utterance = speechReplay.latest();
        if (utterance == null || !speechReady) return;
        if (utterance.localeTag == null) speakText(utterance.text);
        else speakTranslated(utterance.text, utterance.localeTag);
    }

    private void enqueueSpeech(String text, String localeTag) {
        if (text == null || text.trim().isEmpty()) return;
        List<String> segments;
        try {
            segments = SpeechSegmenter.split(text, TextToSpeech.getMaxSpeechInputLength());
        } catch (IllegalArgumentException invalidText) {
            restoreChineseVoice();
            speechStatus.setText("這段文字無法安全分段朗讀。");
            return;
        }
        speechReplay.remember(text, localeTag);
        replaySpeechButton.setEnabled(true);
        if (!requestSpeechAudioFocus()) {
            restoreChineseVoice();
            speechStatus.setText("目前無法取得音訊播放權，回答仍可稍後重播。");
            return;
        }
        String batch = Integer.toString(++utteranceNumber);
        pendingSpeechUtterances.clear();
        List<String> utteranceIds = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            String id = batch + ":" + index;
            utteranceIds.add(id);
            pendingSpeechUtterances.add(id);
        }
        activeUtterance = utteranceIds.get(utteranceIds.size() - 1);
        speechStatus.setText("朗讀中。");
        for (int index = 0; index < segments.size(); index++) {
            int queueMode = index == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            if (tts.speak(segments.get(index), queueMode, null, utteranceIds.get(index)) == TextToSpeech.ERROR) {
                abortSpeech("朗讀失敗。");
                return;
            }
        }
    }

    private void failSpeech(String id) {
        runOnUiThread(() -> {
            if (!isDestroyed() && pendingSpeechUtterances.contains(id)) abortSpeech("朗讀失敗。");
        });
    }

    private void abortSpeech(String message) {
        pendingSpeechUtterances.clear();
        activeUtterance = null;
        if (tts != null) tts.stop();
        restoreChineseVoice();
        abandonSpeechAudioFocus();
        speechStatus.setText(message);
    }

    private void finishSpeech(String id, String message) {
        runOnUiThread(() -> {
            if (!isDestroyed() && pendingSpeechUtterances.remove(id) && id.equals(activeUtterance)) {
                activeUtterance = null;
                pendingSpeechUtterances.clear();
                restoreChineseVoice();
                abandonSpeechAudioFocus();
                speechStatus.setText(message);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private boolean requestSpeechAudioFocus() {
        if (audioManager == null) return false;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            speechFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(speechFocusListener)
                    .build();
            result = audioManager.requestAudioFocus(speechFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(speechFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        speechFocusActive = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!speechFocusActive) speechFocusRequest = null;
        return speechFocusActive;
    }

    @SuppressWarnings("deprecation")
    private void abandonSpeechAudioFocus() {
        if (!speechFocusActive || audioManager == null) {
            speechFocusRequest = null;
            speechFocusActive = false;
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && speechFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(speechFocusRequest);
        } else {
            audioManager.abandonAudioFocus(speechFocusListener);
        }
        speechFocusRequest = null;
        speechFocusActive = false;
    }

    private void onSpeechAudioFocusChange(int focusChange) {
        if (activeUtterance == null || !AudioFocusPolicy.shouldStopSpeech(focusChange)) return;
        activeUtterance = null;
        pendingSpeechUtterances.clear();
        if (tts != null) tts.stop();
        restoreChineseVoice();
        abandonSpeechAudioFocus();
        speechStatus.setText("其他音訊取得播放權，已停止朗讀；可按「重播最近語音」。");
    }

    private void stopSpeech() {
        pendingSpeechUtterances.clear();
        if (activeUtterance == null) {
            restoreChineseVoice();
            abandonSpeechAudioFocus();
            return;
        }
        activeUtterance = null;
        tts.stop();
        restoreChineseVoice();
        abandonSpeechAudioFocus();
        speechStatus.setText("已停止朗讀。");
    }

    private void restoreChineseVoice() {
        if (!translationVoiceActive) return;
        translationVoiceActive = false;
        if (tts != null && chineseVoice != null) tts.setVoice(chineseVoice);
    }

    private EditText addTextInput(String hint) {
        TextInputLayout field = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(hint);
        EditText editor = new TextInputEditText(field.getContext());
        editor.setSaveEnabled(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setMinLines(3);
        field.addView(editor);
        section.addView(field);
        return editor;
    }

    private void requestVoiceInput() {
        if (!voiceInput.isAvailable()) {
            voiceStatus.setText("此裝置目前沒有可用的離線語音辨識，可直接輸入文字。");
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, MICROPHONE_PERMISSION);
        } else {
            cancelLookup();
            stopSpeech();
            voiceInput.start();
        }
    }

    private void copyQuestion() {
        String text = question.getText().toString().trim();
        if (text.isEmpty()) {
            voiceStatus.setText("請先輸入問題。");
            return;
        }
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("語音提問", text));
        voiceStatus.setText("已複製，可貼到官方對話頁面。");
    }

    @SuppressLint("InlinedApi")
    private String[] scanPermissions() {
        return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
    }

    private void requestScan() {
        if (Build.VERSION.SDK_INT < 31) {
            bluetoothStatus.setText("BLE 掃描需 Android 12 以上。仍可從系統設定配對藍牙音箱。");
            return;
        }
        String[] permissions = scanPermissions();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, BLE_PERMISSIONS);
                return;
            }
        }
        startScan();
    }

    private void startScan() {
        devices.removeAllViews();
        ble.scan();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == MAILBOX_MICROPHONE_PERMISSION) {
            if (mailboxDialog != null && mailboxDialog.isShowing()) {
                if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                    requestMailboxRecording();
                } else {
                    mailboxStatus.setText("未允許使用麥克風。");
                }
            }
            return;
        }
        if (request == REMINDER_NOTIFICATIONS) {
            ReminderRequest reminder = pendingReminder;
            pendingReminder = null;
            if (reminder != null) {
                if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                    ReminderReceiver.restore(this);
                    requestReminder(reminder);
                } else {
                    speakAssistantStatus("未允許通知，這次提醒未儲存。");
                }
            }
            return;
        }
        if (request == MICROPHONE_PERMISSION) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                requestVoiceInput();
            } else {
                voiceStatus.setText("未允許使用麥克風，可直接輸入文字。");
            }
            return;
        }
        if (request != BLE_PERMISSIONS) return;
        for (String permission : scanPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                bluetoothStatus.setText("未授予 BLE 掃描所需的權限。");
                return;
            }
        }
        startScan();
    }

    private void updateLastDevice() {
        String name = getPreferences(MODE_PRIVATE).getString(LAST_DEVICE_NAME, "");
        lastDevice.setText(name.isEmpty() ? "" : "上次查看的裝置 " + name);
    }

    private void showDevice(BluetoothDevice device, String name) {
        addButton(devices, name + "\n" + device.getAddress(), () -> {
            getPreferences(MODE_PRIVATE).edit().putString(LAST_DEVICE_NAME, name).apply();
            updateLastDevice();
            ble.inspect(device);
        });
    }

    private void addLink(String title, String uri) {
        addButton(section, title, () -> open(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))),
                androidx.appcompat.R.attr.borderlessButtonStyle);
    }

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException error) {
            android.widget.Toast.makeText(this, "找不到可開啟此頁面的應用程式。", android.widget.Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void chooseAppearance() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("外觀")
                .setSingleChoiceItems(APPEARANCES, appearance, (dialog, choice) -> {
                    dialog.dismiss();
                    if (choice == appearance) return;
                    appearance = choice;
                    appearanceButton.setText("外觀 · " + APPEARANCES[choice]);
                    getPreferences(MODE_PRIVATE).edit().putInt("appearance", choice).apply();
                    getDelegate().setLocalNightMode(NIGHT_MODES[choice]);
                })
                .setNegativeButton("關閉", null)
                .show();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return reply == null ? null : new String[] { reply.getText().toString(), question.getText().toString() };
    }

    private void startSection(String title) {
        startSection(content, title);
    }

    private void startSection(LinearLayout parent, String title) {
        MaterialCardView surface = new MaterialCardView(this);
        surface.setCardElevation(0);
        surface.setStrokeWidth(0);
        surface.setRadius(dp(16));
        surface.setCardBackgroundColor(MaterialColors.getColor(surface,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(12);
        parent.addView(surface, layout);
        section = new LinearLayout(this);
        section.setPadding(dp(16), dp(12), dp(16), dp(12));
        section.setOrientation(LinearLayout.VERTICAL);
        surface.addView(section);
        TextView heading = label(title, 20);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
    }

    private Button addButton(LinearLayout parent, String title, Runnable action) {
        return addButton(parent, title, action, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    }

    private Button addButton(LinearLayout parent, String title, Runnable action, int style) {
        MaterialButton button = new MaterialButton(this, null, style);
        button.setAllCaps(false);
        button.setText(title);
        button.setMinHeight(dp(48));
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private TextView label(String text, int size) {
        TextView view = new MaterialTextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(MaterialColors.getColor(view, size >= 20
                ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
        view.setPadding(0, dp(4), 0, dp(8));
        section.addView(view);
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void updateAudioStatus() {
        java.util.StringJoiner outputs = new java.util.StringJoiner("、");
        if (audioManager != null) {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                switch (device.getType()) {
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                    case AudioDeviceInfo.TYPE_BLE_HEADSET:
                        outputs.add(device.getProductName());
                        break;
                    default:
                }
            }
        }
        audioStatus.setText(outputs.length() == 0 ? "目前沒有可用的藍牙媒體輸出。"
                : "可用的藍牙媒體輸出 " + outputs);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(audioDevices, new Handler(Looper.getMainLooper()));
        }
        updateAudioStatus();
        ReminderReceiver.restore(this);
    }

    private void stopDiagnostics() {
        cancelLookup();
        if (ble != null) ble.pause();
        if (mediaKeys != null) mediaKeys.stop("已結束按鍵測試。");
        if (voiceInput != null) voiceInput.cancel();
        stopSpeech();
    }

    @Override
    protected void onStop() {
        if (voiceMessages.isRecording()) {
            voiceMessages.cancel();
            if (mailboxDialog != null) mailboxStatus.setText("離開畫面，已取消錄音。");
        }
        voiceMessages.stopPlayback();
        stopDiagnostics();
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(audioDevices);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lookupExecutor.shutdownNow();
        chatGptLoginCancelled = true;
        ChatGptProvider.AuthorizationRequest authorization = activeChatGptAuthorization;
        if (authorization != null) authorization.close();
        chatGptExecutor.shutdownNow();
        if (mailboxDialog != null) mailboxDialog.dismiss();
        activeUtterance = null;
        pendingSpeechUtterances.clear();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
