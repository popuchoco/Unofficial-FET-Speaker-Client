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
    private static final Pattern WEEKDAY_OCCURRENCE = Pattern.compile(
            "(?:(?:(\\d{4})年|(今年|明年|去年))?([0-9]{1,2})月|(這個月|本月|下個月|下月|上個月|上月))"
                    + "(?:第([1-5])個|(最後)1個)(?:星期|禮拜)([1-7])(?:(?:是)?(?:幾號|哪天|哪一日))?");
    private static final String[] WEEKDAY_NAMES = { "", "一", "二", "三", "四", "五", "六", "日" };

    private CalendarMonthQuery() {}

    static String answer(String input, Calendar now) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩)", "");
        String query = normalized
                .replaceFirst("^(查詢|查一下|查閱|查看|查)", "")
                .replaceFirst("^(萬年曆|月曆|日曆)", "");
        query = query.replace("星期天", "星期7").replace("星期日", "星期7")
                .replace("禮拜天", "禮拜7").replace("禮拜日", "禮拜7");
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

        Matcher weekdayOccurrence = WEEKDAY_OCCURRENCE.matcher(query);
        if (weekdayOccurrence.matches()) {
            Calendar month = requestedMonth(weekdayOccurrence.group(1), weekdayOccurrence.group(2),
                    weekdayOccurrence.group(3), weekdayOccurrence.group(4), now);
            if (month == null) return "請提供有效的年份與月份。";
            int dayOfWeek = requestedDayOfWeek(Integer.parseInt(weekdayOccurrence.group(7)));
            int lastDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);
            int day;
            if (weekdayOccurrence.group(6) != null) {
                month.set(Calendar.DAY_OF_MONTH, lastDay);
                day = lastDay - (month.get(Calendar.DAY_OF_WEEK) - dayOfWeek + 7) % 7;
                return month.get(Calendar.YEAR) + "年" + (month.get(Calendar.MONTH) + 1) + "月最後一個星期"
                        + WEEKDAY_NAMES[Integer.parseInt(weekdayOccurrence.group(7))] + "是" + day + "日。";
            }
            int firstDayOfWeek = month.get(Calendar.DAY_OF_WEEK);
            int occurrence = Integer.parseInt(weekdayOccurrence.group(5));
            day = 1 + (dayOfWeek - firstDayOfWeek + 7) % 7 + 7 * (occurrence - 1);
            if (day > lastDay) {
                return month.get(Calendar.YEAR) + "年" + (month.get(Calendar.MONTH) + 1) + "月沒有第"
                        + occurrence + "個星期" + WEEKDAY_NAMES[Integer.parseInt(weekdayOccurrence.group(7))] + "。";
            }
            return month.get(Calendar.YEAR) + "年" + (month.get(Calendar.MONTH) + 1) + "月第"
                    + occurrence + "個星期" + WEEKDAY_NAMES[Integer.parseInt(weekdayOccurrence.group(7))]
                    + "是" + day + "日。";
        }

        Matcher explicit = MONTH_LENGTH.matcher(query);
        if (explicit.matches()) {
            Calendar month = requestedMonth(explicit.group(1), explicit.group(2), explicit.group(3), null, now);
            if (month == null) return "請提供有效的年份與月份。";
            return month.get(Calendar.YEAR) + "年" + (month.get(Calendar.MONTH) + 1) + "月有"
                    + month.getActualMaximum(Calendar.DAY_OF_MONTH) + "天。";
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
        Calendar date = requestedMonth(year, month, now);
        if (date == null) return "請提供有效的年份與月份。";
        return year + "年" + month + "月有" + date.getActualMaximum(Calendar.DAY_OF_MONTH) + "天。";
    }

    private static Calendar requestedMonth(String yearText, String relativeYear, String monthText,
                                           String relativeMonth, Calendar now) {
        int year = yearText == null ? now.get(Calendar.YEAR) : Integer.parseInt(yearText);
        if ("明年".equals(relativeYear)) year++;
        else if ("去年".equals(relativeYear)) year--;
        int month;
        if (relativeMonth != null) {
            Calendar current = (Calendar) now.clone();
            current.set(Calendar.DAY_OF_MONTH, 1);
            if ("下個月".equals(relativeMonth) || "下月".equals(relativeMonth)) {
                current.add(Calendar.MONTH, 1);
            } else if ("上個月".equals(relativeMonth) || "上月".equals(relativeMonth)) {
                current.add(Calendar.MONTH, -1);
            }
            year = current.get(Calendar.YEAR);
            month = current.get(Calendar.MONTH) + 1;
        } else {
            month = Integer.parseInt(monthText);
        }
        return requestedMonth(year, month, now);
    }

    private static Calendar requestedMonth(int year, int month, Calendar now) {
        if (year < 1 || year > 9999 || month < 1 || month > 12) return null;
        Calendar date = (Calendar) now.clone();
        date.clear();
        date.setLenient(false);
        date.set(year, month - 1, 1, 12, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        try {
            date.getTimeInMillis();
            return date;
        } catch (IllegalArgumentException invalidDate) {
            return null;
        }
    }

    private static int requestedDayOfWeek(int spokenDay) {
        return spokenDay == 7 ? Calendar.SUNDAY : Calendar.MONDAY + spokenDay - 1;
    }
}
