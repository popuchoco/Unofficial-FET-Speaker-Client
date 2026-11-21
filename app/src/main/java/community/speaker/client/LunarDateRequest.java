package community.speaker.client;

import android.icu.util.ChineseCalendar;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LunarDateRequest {
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日號]");

    final int year;
    final int month;
    final int day;
    final Calendar gregorianDate;
    final String error;

    private LunarDateRequest(int year, int month, int day, Calendar gregorianDate, String error) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.gregorianDate = gregorianDate;
        this.error = error;
    }

    static LunarDateRequest parse(String input, Calendar now) {
        String query = LocalAnswer.replaceChineseNumbers(
                        Normalizer.normalize(input, Normalizer.Form.NFKC))
                .replaceAll("[\\s\\p{Z}]", "");
        if (!query.contains("農曆") && !query.contains("农历")) return null;
        Matcher date = DATE.matcher(query);
        if (!date.find()) return null;

        int year = date.group(1) == null ? relativeYear(query, now) : Integer.parseInt(date.group(1));
        int month = Integer.parseInt(date.group(2));
        int day = Integer.parseInt(date.group(3));
        Calendar gregorian = (Calendar) now.clone();
        gregorian.clear();
        gregorian.setLenient(false);
        gregorian.set(year, month - 1, day, 12, 0, 0);
        gregorian.set(Calendar.MILLISECOND, 0);
        try {
            gregorian.getTimeInMillis();
            return new LunarDateRequest(year, month, day, gregorian, null);
        } catch (IllegalArgumentException invalidDate) {
            return new LunarDateRequest(year, month, day, null, "這個日期不存在，請再確認年月日。");
        }
    }

    String answer() {
        ChineseCalendar lunar = new ChineseCalendar(
                android.icu.util.TimeZone.getTimeZone(gregorianDate.getTimeZone().getID()));
        lunar.setTimeInMillis(gregorianDate.getTimeInMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyyy年M月d日", Locale.TAIWAN);
        format.setTimeZone(gregorianDate.getTimeZone());
        return format.format(gregorianDate.getTime()) + "是農曆"
                + (lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 1 ? "閏" : "")
                + (lunar.get(android.icu.util.Calendar.MONTH) + 1) + "月"
                + lunar.get(android.icu.util.Calendar.DAY_OF_MONTH) + "日。";
    }

    private static int relativeYear(String query, Calendar now) {
        int year = now.get(Calendar.YEAR);
        if (query.contains("明年")) return year + 1;
        if (query.contains("去年")) return year - 1;
        return year;
    }
}
