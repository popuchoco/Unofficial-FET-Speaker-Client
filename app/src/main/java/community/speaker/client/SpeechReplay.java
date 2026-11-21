package community.speaker.client;

final class SpeechReplay {
    static final class Utterance {
        final String text;
        final String localeTag;

        Utterance(String text, String localeTag) {
            this.text = text;
            this.localeTag = localeTag;
        }
    }

    private Utterance latest;

    void remember(String text, String localeTag) {
        latest = new Utterance(text, localeTag);
    }

    Utterance latest() {
        return latest;
    }
}
