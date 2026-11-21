package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SharedTextRequestTest {
    @Test public void trimsSharedAnswerAndIgnoresBlankInput() {
        assertEquals("今天的天氣晴朗。", SharedTextRequest.normalize("  今天的天氣晴朗。  "));
        assertNull(SharedTextRequest.normalize(null));
        assertNull(SharedTextRequest.normalize(" \n  "));
    }

    @Test public void acceptsLongAnswersWithinSegmentedSpeechLimit() {
        StringBuilder longText = new StringBuilder();
        while (longText.length() < 5000) longText.append('答');
        assertEquals(5000, SharedTextRequest.normalize(longText.toString()).length());

        while (longText.length() < SharedTextRequest.MAX_CHARS) longText.append('答');
        assertEquals(SharedTextRequest.MAX_CHARS,
                SharedTextRequest.normalize(longText.toString()).length());
    }

    @Test public void rejectsTextBeyondTheSpeechLimit() {
        StringBuilder longText = new StringBuilder();
        while (longText.length() <= SharedTextRequest.MAX_CHARS) longText.append('答');
        try {
            SharedTextRequest.normalize(longText.toString());
            throw new AssertionError("Expected overlong shared text to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Shared text exceeds the reading limit", expected.getMessage());
        }
    }
}
