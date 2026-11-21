package community.speaker.client;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

final class VoiceMessages {
    private final Context context;
    private final VoiceMessageStore store;
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener focusListener = this::onAudioFocusChange;
    private MediaRecorder recorder;
    private File draft;
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private Consumer<String> playbackStatus;
    private boolean focusRequestActive;
    private boolean focusHeld;
    private boolean prepared;
    private boolean resumeOnFocusGain;

    VoiceMessages(Context context) {
        this.context = context;
        store = new VoiceMessageStore(context.getFilesDir());
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    boolean isRecording() {
        return recorder != null;
    }

    @SuppressWarnings("deprecation")
    void record(Consumer<String> status) {
        cancel();
        stopPlayback();
        try {
            draft = store.begin();
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(draft.getAbsolutePath());
            recorder.setOnErrorListener((session, error, detail) -> {
                if (recorder != session) return;
                cancel();
                status.accept("錄音中斷，這段留言未儲存。");
            });
            recorder.prepare();
            recorder.start();
        } catch (IOException | RuntimeException error) {
            cancel();
            status.accept("無法開始錄音，請確認麥克風可用。");
        }
    }

    void save() throws IOException {
        if (!isRecording()) throw new IOException("No active recording");
        try {
            recorder.stop();
            releaseRecorder();
            store.save(draft);
            draft = null;
        } catch (RuntimeException error) {
            throw new IOException("Recording is too short or interrupted", error);
        } finally {
            cancel();
        }
    }

    void cancel() {
        releaseRecorder();
        if (draft != null) {
            store.discard(draft);
            draft = null;
        }
    }

    List<File> list() throws IOException {
        return store.list();
    }

    boolean delete(File recording) {
        stopPlayback();
        return store.delete(recording);
    }

    void play(File recording, Consumer<String> status) {
        stopPlayback();
        if (!requestAudioFocus()) {
            status.accept("目前無法取得音訊播放權，請等其他音訊結束後再重試。");
            return;
        }
        playbackStatus = status;
        resumeOnFocusGain = true;
        MediaPlayer session = new MediaPlayer();
        player = session;
        try {
            session.setAudioAttributes(speechAttributes());
            session.setDataSource(recording.getAbsolutePath());
            session.setOnPreparedListener(ready -> {
                if (player == ready) {
                    prepared = true;
                    if (focusHeld && resumeOnFocusGain) {
                        ready.start();
                        resumeOnFocusGain = false;
                    }
                }
            });
            session.setOnCompletionListener(complete -> {
                if (player == complete) {
                    stopPlayback();
                    status.accept("留言播放完畢。");
                }
            });
            session.setOnErrorListener((failed, error, detail) -> {
                if (player == failed) {
                    stopPlayback();
                    status.accept("無法播放這段留言。");
                }
                return true;
            });
            session.prepareAsync();
        } catch (IOException | RuntimeException error) {
            stopPlayback();
            status.accept("無法播放這段留言。");
        }
    }

    void stopPlayback() {
        MediaPlayer previous = player;
        player = null;
        prepared = false;
        focusHeld = false;
        resumeOnFocusGain = false;
        playbackStatus = null;
        if (previous != null) previous.release();
        abandonAudioFocus();
    }

    @SuppressWarnings("deprecation")
    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(speechAttributes())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(focusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        focusRequestActive = focusHeld;
        if (!focusHeld) focusRequest = null;
        return focusHeld;
    }

    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        if (!focusRequestActive || audioManager == null) {
            focusRequest = null;
            focusRequestActive = false;
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(focusListener);
        }
        focusRequest = null;
        focusRequestActive = false;
    }

    private void onAudioFocusChange(int focusChange) {
        if (player == null) return;
        AudioFocusPolicy.Action action = AudioFocusPolicy.actionFor(focusChange);
        if (action == AudioFocusPolicy.Action.RESUME) {
            focusHeld = true;
            if (prepared && resumeOnFocusGain) {
                if (!player.isPlaying()) player.start();
                resumeOnFocusGain = false;
                if (playbackStatus != null) playbackStatus.accept("音訊恢復，繼續播放留言。");
            }
        } else if (action == AudioFocusPolicy.Action.PAUSE) {
            focusHeld = false;
            if (prepared && player.isPlaying()) {
                player.pause();
                resumeOnFocusGain = true;
                if (playbackStatus != null) playbackStatus.accept("留言播放暫停，音訊恢復後會繼續。");
            }
        } else if (action == AudioFocusPolicy.Action.STOP) {
            Consumer<String> status = playbackStatus;
            stopPlayback();
            if (status != null) status.accept("其他音訊取得播放權，留言已停止；如需繼續請重新播放。");
        }
    }

    private static AudioAttributes speechAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void releaseRecorder() {
        MediaRecorder previous = recorder;
        recorder = null;
        if (previous != null) previous.release();
    }
}
