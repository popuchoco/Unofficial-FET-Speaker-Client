package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class LocalActionRequest {
    enum Action { OPEN_REMINDERS, OPEN_MAILBOX }

    private static final Pattern MAILBOX = Pattern.compile(
            "(?:(?:我要|我想)?(?:開啟|打開|查看|播放|管理|顯示|列出)?(?:我的|本機)?)"
                    + "(?:家庭)?留言(?:機)?(?:清單)?(?:有哪些|嗎)?");
    private static final Pattern REMINDERS = Pattern.compile(
            "(?:(?:(?:查看|顯示|列出|開啟|打開|管理|取消)(?:我的|本機)?(?:所有)?"
                    + "|我有(?:哪些|幾個)?)(?:本機)?提醒(?:事項)?(?:清單)?(?:嗎|有哪些)?"
                    + "|(?:本機)?提醒(?:事項)?清單)");

    private LocalActionRequest() {}

    static Action parse(String speech) {
        if (speech == null) return null;
        String query = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩|我要|我想)", "");
        if (MAILBOX.matcher(query).matches()) return Action.OPEN_MAILBOX;
        if (REMINDERS.matcher(query).matches()) return Action.OPEN_REMINDERS;
        return null;
    }
}
