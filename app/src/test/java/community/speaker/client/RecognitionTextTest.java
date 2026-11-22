package community.speaker.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RecognitionTextTest {
    @Test public void returnsTheFirstNonEmptyCandidateTrimmed() {
        assertEquals("查詢台北天氣", RecognitionText.firstUsable(
                Arrays.asList("  ", " 查詢台北天氣 ", "另一個候選")));
    }

    @Test public void reportsNoUsableCandidateForMissingResults() {
        assertNull(RecognitionText.firstUsable(null));
        assertNull(RecognitionText.firstUsable(Collections.emptyList()));
        assertNull(RecognitionText.firstUsable(Arrays.asList(null, "  ")));
    }
}
