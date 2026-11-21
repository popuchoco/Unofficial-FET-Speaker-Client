package community.speaker.client;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.function.Consumer;

final class MediaKeyDiagnostic {
    private final Context context;
    private final Consumer<String> status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> stop("按鍵測試已結束。");
    private MediaSession session;
    private int count;
    private String lastKey;

    MediaKeyDiagnostic(Context context, Consumer<String> status) {
        this.context = context;
        this.status = status;
    }

    @SuppressWarnings("deprecation")
    void start() {
        release();
        count = 0;
        lastKey = null;
        MediaSession diagnostic = new MediaSession(context, "SpeakerKeyDiagnostic");
        session = diagnostic;
        if (Build.VERSION.SDK_INT < 26) {
            diagnostic.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        }
        diagnostic.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                if (session != diagnostic || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return false;
                KeyEvent event = Build.VERSION.SDK_INT >= 33
                        ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class)
                        : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    count++;
                    lastKey = KeyEvent.keyCodeToString(event.getKeyCode());
                    status.accept("已收到 " + count + " 次媒體按鍵。最近事件 "
                            + lastKey);
                }
                return true;
            }
        }, main);
        diagnostic.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build());
        diagnostic.setActive(true);
        status.accept("請按一下音箱上的媒體按鍵。測試會在 30 秒後停止。");
        main.postDelayed(timeout, 30_000);
    }

    void stop(String message) {
        if (session == null) return;
        release();
        status.accept(count == 0
                ? message + " Android 未將媒體按鍵送到這個測試。"
                : message + " 共收到 " + count + " 次媒體按鍵。最近事件 " + lastKey);
    }

    private void release() {
        main.removeCallbacks(timeout);
        MediaSession previous = session;
        session = null;
        if (previous != null) previous.release();
    }
}
