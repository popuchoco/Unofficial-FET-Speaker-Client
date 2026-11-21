package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranslationRequest {
    private static final Pattern COMMAND = Pattern.compile(
            "^(?:請)?(?:(?:把|將))?(.+?)(?:翻譯成|翻成|翻譯為|翻為)([\\p{L}]+)$");

    final String text;
    final String targetCode;
    final String targetName;
    final String ttsLocale;
    final String error;

    private TranslationRequest(String text, String targetCode, String targetName,
                               String ttsLocale, String error) {
        this.text = text;
        this.targetCode = targetCode;
        this.targetName = targetName;
        this.ttsLocale = ttsLocale;
        this.error = error;
    }

    static TranslationRequest parse(String input) {
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC)
                .replaceAll("[。.!?！？]+$", "");
        Matcher command = COMMAND.matcher(normalized);
        if (!command.matches()) return null;
        String text = command.group(1).replaceAll("^[「『\\\"']|[」』\\\"']$", "").trim();
        if (text.isEmpty()) return null;
        if (text.codePointCount(0, text.length()) > 5000) {
            return invalid(text, "Papago 單次最多翻譯五千字，請分段輸入。");
        }

        String target = command.group(2);
        switch (target) {
            case "英文": case "英語": case "English":
                return new TranslationRequest(text, "en", "英文", "en-US", null);
            case "日文": case "日語": case "Japanese":
                return new TranslationRequest(text, "ja", "日文", "ja-JP", null);
            case "韓文": case "韓語": case "Korean":
                return new TranslationRequest(text, "ko", "韓文", "ko-KR", null);
            case "繁體中文": case "正體中文": case "中文":
                return new TranslationRequest(text, "zh-TW", "繁體中文", "zh-TW", null);
            case "簡體中文":
                return new TranslationRequest(text, "zh-CN", "簡體中文", "zh-CN", null);
            default:
                return invalid(text, "目前支援英文、日文、韓文、繁體中文及簡體中文。");
        }
    }

    private static TranslationRequest invalid(String text, String error) {
        return new TranslationRequest(text, null, null, null, error);
    }
}
