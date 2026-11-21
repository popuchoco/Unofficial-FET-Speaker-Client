package community.speaker.client;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeechSegmenterTest {
    @Test public void prefersSentenceBoundariesAndPreservesAllText() {
        String text = "第一句。第二句。第三句。";
        List<String> segments = SpeechSegmenter.split(text, 5);

        assertEquals(text, join(segments));
        assertEquals("第一句。", segments.get(0));
        assertEquals("第二句。", segments.get(1));
        assertEquals("第三句。", segments.get(2));
    }

    @Test public void hardSplitsLongUnpunctuatedTextWithinTheLimit() {
        String text = "abcdefghijk";
        List<String> segments = SpeechSegmenter.split(text, 4);

        assertEquals(text, join(segments));
        for (String segment : segments) assertTrue(segment.length() <= 4);
    }

    @Test public void neverSplitsASurrogatePair() {
        String text = "A😀BC";
        List<String> segments = SpeechSegmenter.split(text, 3);

        assertEquals(text, join(segments));
        for (String segment : segments) assertTrue(segment.length() <= 3);
        assertEquals("A😀", segments.get(0));
    }

    private static String join(List<String> segments) {
        StringBuilder result = new StringBuilder();
        for (String segment : segments) result.append(segment);
        return result.toString();
    }
}
