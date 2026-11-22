package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class AlarmManagementRequest {
    private static final Pattern SHOW_ALARMS = Pattern.compile(
            "(?:(?:查看|顯示|列出|管理|取消|刪除|停用|關閉|關掉|打開|開啟)"
                    + "(?:我的|目前|本機|所有)?|我的|目前|本機|所有)?"
                    + "鬧鐘(?:清單|列表)?");
    private static final Pattern CANCEL_ALARM = Pattern.compile("(?:取消|刪除|停用|關閉|關掉).+鬧鐘");

    private AlarmManagementRequest() {}

    static boolean shouldShowAlarms(String speech) {
        if (speech == null) return false;
        String query = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩|我要|我想)", "");
        return SHOW_ALARMS.matcher(query).matches() || CANCEL_ALARM.matcher(query).matches();
    }
}
