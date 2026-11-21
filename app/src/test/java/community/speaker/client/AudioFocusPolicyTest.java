package community.speaker.client;

import android.media.AudioManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AudioFocusPolicyTest {
    @Test public void resumesAfterAudioReturns() {
        assertEquals(AudioFocusPolicy.Action.RESUME,
                AudioFocusPolicy.actionFor(AudioManager.AUDIOFOCUS_GAIN));
    }

    @Test public void pausesForTransientLossAndSpeechDucking() {
        assertEquals(AudioFocusPolicy.Action.PAUSE,
                AudioFocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT));
        assertEquals(AudioFocusPolicy.Action.PAUSE,
                AudioFocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK));
    }

    @Test public void stopsAfterPermanentLoss() {
        assertEquals(AudioFocusPolicy.Action.STOP,
                AudioFocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS));
    }

    @Test public void ignoresUnknownFocusChanges() {
        assertEquals(AudioFocusPolicy.Action.IGNORE, AudioFocusPolicy.actionFor(0));
    }
}
