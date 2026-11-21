package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpeechReplayTest {
    @Test public void storesOnlyTheLatestUtteranceAndItsVoice() {
        SpeechReplay replay = new SpeechReplay();
        assertNull(replay.latest());

        replay.remember("Bonjour", "fr-FR");
        assertEquals("Bonjour", replay.latest().text);
        assertEquals("fr-FR", replay.latest().localeTag);

        replay.remember("你好", null);
        assertEquals("你好", replay.latest().text);
        assertNull(replay.latest().localeTag);
    }
}
