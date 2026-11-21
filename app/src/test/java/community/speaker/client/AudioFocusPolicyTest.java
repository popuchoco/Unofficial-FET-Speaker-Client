package community.speaker.client;

import android.media.AudioManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test public void spokenAnswersStopWhenFocusMovesToOtherAudio() {
        assertTrue(AudioFocusPolicy.shouldStopSpeech(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT));
        assertTrue(AudioFocusPolicy.shouldStopSpeech(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK));
        assertTrue(AudioFocusPolicy.shouldStopSpeech(AudioManager.AUDIOFOCUS_LOSS));
        assertFalse(AudioFocusPolicy.shouldStopSpeech(AudioManager.AUDIOFOCUS_GAIN));
        assertFalse(AudioFocusPolicy.shouldStopSpeech(0));
    }
}
