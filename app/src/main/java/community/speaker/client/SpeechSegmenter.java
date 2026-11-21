package community.speaker.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SpeechSegmenter {
    private SpeechSegmenter() {}

    static List<String> split(String text, int maxLength) {
        if (maxLength < 1) throw new IllegalArgumentException("Maximum length must be positive");
        if (text == null || text.isEmpty()) return Collections.emptyList();

        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardLimit = Math.min(text.length(), start + maxLength);
            int end = hardLimit;
            if (hardLimit < text.length()) {
                end = safeBoundary(text, hardLimit, start);
                int preferred = lastBoundary(text, start, end, Boundary.SENTENCE);
                if (preferred < 0) preferred = lastBoundary(text, start, end, Boundary.PAUSE);
                if (preferred < 0) preferred = lastBoundary(text, start, end, Boundary.WHITESPACE);
                if (preferred > start) end = preferred;
            }
            if (end <= start) {
                throw new IllegalArgumentException("Maximum length is too short for a Unicode character");
            }
            segments.add(text.substring(start, end));
            start = end;
        }
        return Collections.unmodifiableList(segments);
    }

    private static int safeBoundary(String text, int boundary, int start) {
        if (boundary > start && boundary < text.length()
                && Character.isHighSurrogate(text.charAt(boundary - 1))
                && Character.isLowSurrogate(text.charAt(boundary))) {
            return boundary - 1;
        }
        return boundary;
    }

    private static int lastBoundary(String text, int start, int limit, Boundary type) {
        int minimum = start + Math.max(1, (limit - start) / 2);
        for (int index = limit - 1; index >= minimum; index--) {
            char value = text.charAt(index);
            if (type == Boundary.SENTENCE && isSentenceEnd(value)) return index + 1;
            if (type == Boundary.PAUSE && isPause(value)) return index + 1;
            if (type == Boundary.WHITESPACE && Character.isWhitespace(value)) return index + 1;
        }
        return -1;
    }

    private static boolean isSentenceEnd(char value) {
        return value == '。' || value == '！' || value == '？' || value == '.'
                || value == '!' || value == '?' || value == '；' || value == ';'
                || value == '\n' || value == '\r';
    }

    private static boolean isPause(char value) {
        return value == '，' || value == ',' || value == '、' || value == ':' || value == '：';
    }

    private enum Boundary { SENTENCE, PAUSE, WHITESPACE }
}
