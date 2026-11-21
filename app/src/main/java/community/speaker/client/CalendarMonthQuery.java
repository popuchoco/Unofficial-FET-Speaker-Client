package community.speaker.client;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CalendarMonthQuery {
    private static final Pattern RELATIVE_MONTH = Pattern.compile(
            "(這個月|本月|下個月|下月|上個月|上月)(?:有)?(?:幾天|多少天|天數)");
    private static final Pattern MONTH_LENGTH = Pattern.compile(
            "(?:(\\d{4})年|(今年|明年|去年))?([0-9]{1,2})月(?:份)?(?:(?:有)?(?:幾天|多少天|天數))");

    private CalendarMonthQuery() {}

    static String answer(String input, Calendar now) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩)", "");
        String query = normalized
                .replaceFirst("^(查詢|查一下|查閱|查看|查)", "")
                .replaceFirst("^(萬年曆|月曆|日曆)", "");
        query = LocalAnswer.replaceChineseNumbers(query);

        Matcher relative = RELATIVE_MONTH.matcher(query);
        if (relative.matches()) {
            Calendar month = (Calendar) now.clone();
            month.set(Calendar.DAY_OF_MONTH, 1);
            String period = relative.group(1);
            if ("下個月".equals(period) || "下月".equals(period)) month.add(Calendar.MONTH, 1);
            else if ("上個月".equals(period) || "上月".equals(period)) month.add(Calendar.MONTH, -1);
            return monthLength(month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, now);
        }

        Matcher explicit = MONTH_LENGTH.matcher(query);
        if (explicit.matches()) {
            int year = explicit.group(1) == null ? now.get(Calendar.YEAR) : Integer.parseInt(explicit.group(1));
            String relativeYear = explicit.group(2);
            if ("明年".equals(relativeYear)) year++;
            else if ("去年".equals(relativeYear)) year--;
            int month = Integer.parseInt(explicit.group(3));
            return monthLength(year, month, now);
        }

        if (normalized.contains("萬年曆") || normalized.contains("月曆") || normalized.contains("日曆")) {
            if (!query.contains("萬年曆") && !query.contains("月曆") && !query.contains("日曆")) {
                String delegated = LocalAnswer.reply(query, now);
                if (delegated != null) return delegated;
            }
            return "請指定要查詢的年月，例如「2024年2月有幾天」。";
        }
        return null;
    }

    private static String monthLength(int year, int month, Calendar now) {
        if (year < 1 || year > 9999 || month < 1 || month > 12) {
            return "請提供有效的年份與月份。";
        }
        Calendar date = (Calendar) now.clone();
        date.clear();
        date.setLenient(false);
        date.set(year, month - 1, 1, 12, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        try {
            date.getTimeInMillis();
            return year + "年" + month + "月有" + date.getActualMaximum(Calendar.DAY_OF_MONTH) + "天。";
        } catch (IllegalArgumentException invalidDate) {
            return "請提供有效的年份與月份。";
        }
    }
}
