package community.speaker.client;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.function.Consumer;

final class VoiceInput {
    private final Context context;
    private final Consumer<String> status;
    private final Consumer<String> transcript;
    private SpeechRecognizer session;

    VoiceInput(Context context, Consumer<String> status, Consumer<String> transcript) {
        this.context = context;
        this.status = status;
        this.transcript = transcript;
    }

    boolean isAvailable() {
        return Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    void start() {
        if (session != null) return;
        if (Build.VERSION.SDK_INT < 31 || !isAvailable()) {
            status.accept("此裝置目前沒有可用的離線語音辨識。");
            return;
        }
        try {
            SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            session = recognizer;
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    if (session == recognizer) status.accept("請說話。");
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float value) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    if (session == recognizer) status.accept("正在辨識…");
                }
                @Override public void onError(int error) {
                    if (session != recognizer) return;
                    release();
                    switch (error) {
                        case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                        case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                            status.accept("請在裝置上啟用繁體中文離線辨識，或輸入文字。");
                            break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            status.accept("沒有辨識到語音。請再試一次。");
                            break;
                        default:
                            status.accept("語音辨識暫時無法使用。請再試一次，或輸入文字。");
                    }
                }
                @Override public void onResults(Bundle results) {
                    if (session != recognizer) return;
                    release();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches == null || matches.isEmpty() || matches.get(0).trim().isEmpty()) {
                        status.accept("沒有辨識到語音。請再試一次。");
                        return;
                    }
                    transcript.accept(matches.get(0));
                }
                @Override public void onPartialResults(Bundle results) { }
                @Override public void onEvent(int type, Bundle params) { }
            });
            Intent options = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            status.accept("正在啟動麥克風…");
            recognizer.startListening(options);
        } catch (UnsupportedOperationException | SecurityException error) {
            release();
            status.accept("無法開啟離線語音辨識。請確認麥克風權限與裝置設定。");
        }
    }

    void cancel() {
        if (session == null) return;
        SpeechRecognizer recognizer = session;
        session = null;
        recognizer.cancel();
        recognizer.destroy();
        status.accept("已停止語音輸入。");
    }

    private void release() {
        SpeechRecognizer recognizer = session;
        session = null;
        if (recognizer != null) recognizer.destroy();
    }
}
