package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class TranslationRequestTest {
    @Test public void parsesTraditionalChineseToJapanese() {
        TranslationRequest request = TranslationRequest.parse("把你好翻成日文");
        assertEquals("你好", request.text);
        assertEquals("ja", request.targetCode);
        assertEquals("日文", request.targetName);
        assertEquals("ja-JP", request.ttsLocale);
        assertNull(request.error);
    }

    @Test public void acceptsEnglishAndTraditionalChineseTargets() {
        assertEquals("en", TranslationRequest.parse("請將 good morning 翻譯成英文").targetCode);
        assertEquals("zh-TW", TranslationRequest.parse("把 Hello 翻成繁體中文").targetCode);
        assertEquals("zh-CN", TranslationRequest.parse("把 Hello 翻成簡體中文").targetCode);
    }

    @Test public void ignoresOrdinaryQuestionsAndExplainsLimits() {
        assertNull(TranslationRequest.parse("明天天氣如何"));
        assertNotNull(TranslationRequest.parse("把你好翻成阿拉伯文").error);
        char[] longText = new char[5001];
        java.util.Arrays.fill(longText, 'a');
        assertNotNull(TranslationRequest.parse("把 " + new String(longText) + " 翻成日文").error);
    }
}
