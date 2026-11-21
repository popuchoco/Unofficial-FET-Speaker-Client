package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PapagoLookupTest {
    @Test public void extractsTranslationAndKeepsPapagoAttribution() throws Exception {
        TranslationRequest request = TranslationRequest.parse("把你好翻成日文");
        LookupAnswer answer = PapagoLookup.answer(
                "{\"message\":{\"result\":{\"translatedText\":\"こんにちは\"}}}", request);

        assertEquals("Papago 翻譯：こんにちは", answer.text);
        assertEquals("こんにちは", answer.speechText);
        assertEquals("ja-JP", answer.speechLocale);
        assertEquals(PapagoLookup.SOURCE, answer.source);
    }
}
