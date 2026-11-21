package community.speaker.client;

final class LookupAnswer {
    final String text;
    final String source;
    final String speechText;
    final String speechLocale;

    LookupAnswer(String text, String source) {
        this(text, source, null, null);
    }

    LookupAnswer(String text, String source, String speechText, String speechLocale) {
        this.text = text;
        this.source = source;
        this.speechText = speechText;
        this.speechLocale = speechLocale;
    }
}
