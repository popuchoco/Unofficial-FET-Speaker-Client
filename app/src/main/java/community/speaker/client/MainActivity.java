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
    private TextView speechStatus;
    private TextView bluetoothStatus;
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

        label("愛講音箱", 28);
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

        startSection("服務設定");
        label("在官方頁面自行登入。", 15);
        addLink("開啟 ChatGPT", "https://chatgpt.com/");
        addLink("開啟 Gemini", "https://gemini.google.com/app");
        addLink("開啟 Claude", "https://claude.ai/");
        addLink("開啟 KKBOX", "https://www.kkbox.com/tw/");

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
        voiceInput = new VoiceInput(this, voiceStatus::setText, question::setText);
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
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
        }
        return new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
    }

    private void requestScan() {
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

    private void showDevice(BluetoothDevice device, String description) {
        addButton(devices, description, () -> ble.inspect(device));
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
    }

    private void stopDiagnostics() {
        if (ble != null) ble.pause();
        if (mediaKeys != null) mediaKeys.stop("已結束按鍵測試。");
        if (voiceInput != null) voiceInput.cancel();
        stopSpeech();
    }

    @Override
    protected void onStop() {
        stopDiagnostics();
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(audioDevices);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activeUtterance = null;
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
