package community.speaker.client;

import android.Manifest;
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
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
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

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private static final int BLE_PERMISSIONS = 42;
    private static final int MICROPHONE_PERMISSION = 43;
    private static final int REMINDER_NOTIFICATIONS = 44;
    private static final int MAILBOX_MICROPHONE_PERMISSION = 45;
    private static final String LAST_DEVICE_NAME = "last_device_name";
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
    private final AudioDeviceCallback audioDevices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { updateAudioStatus(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { updateAudioStatus(); }
    };
    private TextToSpeech tts;
    private BleInspector ble;
    private MediaKeyDiagnostic mediaKeys;
    private boolean speechReady;
    private int utteranceNumber;
    private String activeUtterance;

    @Override
    protected void onCreate(Bundle state) {
        appearance = getPreferences(MODE_PRIVATE).getInt("appearance", 0);
        getDelegate().setLocalNightMode(NIGHT_MODES[appearance]);
        super.onCreate(state);
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
        content.requestFocus();
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
        label("音箱喚醒與自動回應尚未開放。", 15);

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
    }

    private void buildDiagnostics(LinearLayout diagnostics) {
        startSection(diagnostics, "收音測試");
        question = addTextInput("輸入或辨識問題");
        voiceStatus = label("", 15);
        voiceStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        voiceInput = new VoiceInput(this, voiceStatus::setText, transcript -> {
            question.setText(transcript);
            answerLocalQuestion();
        });
        addButton(section, "測試助理指令", this::answerLocalQuestion);
        lookupSource = addButton(section, "開啟資料來源", () -> {
            if (lookupSourceUrl != null) open(new Intent(Intent.ACTION_VIEW, Uri.parse(lookupSourceUrl)));
        }, androidx.appcompat.R.attr.borderlessButtonStyle);
        lookupSource.setVisibility(View.GONE);
        addButton(section, "開始語音輸入", this::requestVoiceInput);
        addButton(section, "取消語音輸入", voiceInput::cancel, androidx.appcompat.R.attr.borderlessButtonStyle);
        addButton(section, "複製問題", this::copyQuestion, androidx.appcompat.R.attr.borderlessButtonStyle);

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
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { finishSpeech(id, "朗讀完成。"); }
            @Override public void onError(String id) { finishSpeech(id, "朗讀失敗。"); }
        });
        speechReady = true;
        speakButton.setEnabled(true);
        speechStatus.setText("本機中文語音就緒。");
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
            voiceStatus.setText("這個問題目前無法離線回答。");
            return;
        }
        voiceStatus.setText(answer);
        if (speechReady) speakText(answer);
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
        stopSpeech();
        int generation = lookupGeneration;
        voiceStatus.setText(loading);
        lookupTask = lookupExecutor.submit(() -> {
            LookupAnswer response;
            try {
                response = lookup.fetch();
            } catch (java.io.IOException | org.json.JSONException error) {
                response = new LookupAnswer(failure, null);
            }
            LookupAnswer answer = response;
            runOnUiThread(() -> {
                if (lookupGeneration != generation || isDestroyed()) return;
                lookupTask = null;
                lookupSourceUrl = answer.source;
                lookupSource.setVisibility(lookupSourceUrl == null ? View.GONE : View.VISIBLE);
                voiceStatus.setText(answer.text);
                if (speechReady) speakText(answer.text);
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
        if (request.seconds == 0) {
            reportReminder("請說明多久後要提醒什麼，例如三分鐘後提醒我關火。");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = request;
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, REMINDER_NOTIFICATIONS);
            return;
        }
        if (!ReminderReceiver.notificationsAvailable(this)) {
            reportReminder("請先在 Android 設定開啟愛狐說的通知。");
            return;
        }
        ReminderReceiver.Entry entry = ReminderReceiver.add(this, request);
        if (entry == null) {
            reportReminder("未能儲存提醒，請再試一次。");
            return;
        }
        String time = android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(entry.due));
        reportReminder("已安排 " + time + " 提醒你" + entry.message + "。通知可能因省電設定延後。");
    }

    private void reportReminder(String message) {
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
            for (java.io.File recording : recordings) {
                String title = times.format(new java.util.Date(recording.lastModified()));
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
        if (text.length() > TextToSpeech.getMaxSpeechInputLength()) {
            speechStatus.setText("文字超過單次朗讀上限，請分段貼上。");
            return;
        }
        activeUtterance = Integer.toString(++utteranceNumber);
        speechStatus.setText("朗讀中。");
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeUtterance) == TextToSpeech.ERROR) {
            finishSpeech(activeUtterance, "朗讀失敗。");
        }
    }

    private void finishSpeech(String id, String message) {
        runOnUiThread(() -> {
            if (!isDestroyed() && id != null && id.equals(activeUtterance)) {
                activeUtterance = null;
                speechStatus.setText(message);
            }
        });
    }

    private void stopSpeech() {
        if (activeUtterance == null) return;
        activeUtterance = null;
        tts.stop();
        speechStatus.setText("已停止朗讀。");
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
                    reportReminder("未允許通知，這次提醒未儲存。");
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

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            android.widget.Toast.makeText(this, "找不到可開啟此頁面的應用程式。", android.widget.Toast.LENGTH_SHORT).show();
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
        if (mailboxDialog != null) mailboxDialog.dismiss();
        activeUtterance = null;
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
