package community.speaker.client;

import android.media.AudioManager;

final class AudioFocusPolicy {
    enum Action { RESUME, PAUSE, STOP, IGNORE }

    private AudioFocusPolicy() {}

    static Action actionFor(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                return Action.RESUME;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                return Action.PAUSE;
            case AudioManager.AUDIOFOCUS_LOSS:
                return Action.STOP;
            default:
                return Action.IGNORE;
        }
    }
}
