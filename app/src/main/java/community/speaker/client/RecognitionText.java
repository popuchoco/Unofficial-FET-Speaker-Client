package community.speaker.client;

import java.util.List;

final class RecognitionText {
    private RecognitionText() {}

    static String firstUsable(List<String> candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String text = candidate.trim();
            if (!text.isEmpty()) return text;
        }
        return null;
    }
}
