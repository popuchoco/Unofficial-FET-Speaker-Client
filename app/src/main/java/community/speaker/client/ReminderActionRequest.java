package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReminderActionRequest {
    private static final Pattern CANCEL = Pattern.compile(
            "(?:取消|刪除|刪掉)(?:下一個|最近的?|最早的?|第([1-9][0-9]{0,3})(?:個|筆|則)?)"
                    + "(?:本機)?提醒(?:事項)?");

    final int number;

    private ReminderActionRequest(int number) {
        this.number = number;
    }

    static ReminderActionRequest parse(String speech) {
        if (speech == null) return null;
        String query = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩|我要|我想)", "");
        query = query.replace("下一個", "最近的");
        Matcher cancel = CANCEL.matcher(LocalAnswer.replaceChineseNumbers(query));
        if (!cancel.matches()) return null;
        return new ReminderActionRequest(cancel.group(1) == null ? 1 : Integer.parseInt(cancel.group(1)));
    }
}
