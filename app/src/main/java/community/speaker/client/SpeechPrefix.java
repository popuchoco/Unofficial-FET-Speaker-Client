package community.speaker.client;

final class SpeechPrefix {
    private static final String POLITE_PREFIX =
            "^(?:請問(?:一下)?|請教(?:一下)?|麻煩請幫我|麻煩幫我|麻煩請|麻煩|"
                    + "可以幫我|請幫我|幫我|請|我要|我想)";

    private SpeechPrefix() {}

    static String strip(String normalizedText) {
        String result = normalizedText;
        while (true) {
            String stripped = result.replaceFirst(POLITE_PREFIX, "");
            if (stripped.equals(result)) return result;
            result = stripped;
        }
    }
}
