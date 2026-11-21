package community.speaker.client;

import android.content.Context;
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
    private MediaRecorder recorder;
    private File draft;
    private MediaPlayer player;

    VoiceMessages(Context context) {
        this.context = context;
        store = new VoiceMessageStore(context.getFilesDir());
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
        MediaPlayer session = new MediaPlayer();
        player = session;
        try {
            session.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            session.setDataSource(recording.getAbsolutePath());
            session.setOnPreparedListener(ready -> {
                if (player == ready) ready.start();
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
        if (previous != null) previous.release();
    }

    private void releaseRecorder() {
        MediaRecorder previous = recorder;
        recorder = null;
        if (previous != null) previous.release();
    }
}
