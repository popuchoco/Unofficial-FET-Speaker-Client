package community.speaker.client;

final class SharedTextRequest {
    static final int MAX_CHARS = 20_000;

    private SharedTextRequest() {}

    static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String normalized = text.trim();
        if (normalized.length() > MAX_CHARS) {
            throw new IllegalArgumentException("Shared text exceeds the reading limit");
        }
        return normalized;
    }
}
