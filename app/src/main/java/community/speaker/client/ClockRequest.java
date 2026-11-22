package community.speaker.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClockRequest {
    enum Kind { ALARM, TIMER, CLARIFICATION }

    private static final Pattern ALARM = Pattern.compile(
            "(?:設定|設置|設個|設一個|設1個)?(?:鬧鐘|叫醒我)?(今天|明天)?"
                    + "(每天|每日|平日|工作日|週末|周末|"
                    + "每(?:週|周|星期|禮拜)(?:星期|禮拜)?[一二三四五六日天1-7]|"
                    + "每(?:週|周|星期|禮拜)?(?:星期|禮拜)?[一二三四五六日天1-7](?:到|至)"
                    + "(?:週|周|星期|禮拜)?[一二三四五六日天1-7])?"
                    + "(凌晨|午夜|早上|上午|中午|下午|晚上)?([0-9]{1,2})(?:點鐘?|:)"
                    + "(?:(半)|([0-9]{1,2})(?:分(?:鐘)?)?)?"
                    + "(?:的)?(?:鬧鐘|叫醒我)?");
    private static final Pattern TIMER = Pattern.compile(
            "(?:設定|設置|開始)?(?:倒數|計時器?)?(?:([0-9]{1,5}|半)(?:個)?小時)?"
                    + "(?:([0-9]{1,5}|半)分鐘?)?(?:([0-9]{1,5})秒(?:鐘)?)?(?:的)?(?:倒數|計時器?)?");
    private static final Pattern WEEKDAY_RANGE = Pattern.compile(
            "每(?:週|周|星期|禮拜)?(?:星期|禮拜)?([一二三四五六日天1-7])(?:到|至)"
                    + "(?:週|周|星期|禮拜)?([一二三四五六日天1-7])");
    final Kind kind;
    final int hour;
    final int minute;
    final int seconds;
    final ArrayList<Integer> repeatDays;
    final String prompt;

    private ClockRequest(Kind kind, int hour, int minute, int seconds,
                         ArrayList<Integer> repeatDays, String prompt) {
        this.kind = kind;
        this.hour = hour;
        this.minute = minute;
        this.seconds = seconds;
        this.repeatDays = repeatDays;
        this.prompt = prompt;
    }

    static ClockRequest parse(String speech) {
        String normalized = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請幫我|幫我|請)", "");
        boolean timer = normalized.contains("倒數") || normalized.contains("計時");
        boolean alarm = normalized.contains("鬧鐘") || normalized.contains("叫醒我");
        if (!timer && !alarm) return null;
        if (timer && alarm) return clarify("請分開說要設定的鬧鐘和倒數。");

        // A clock separator must stay separate from spoken decimal numbers.
        String timerWords = normalized.replace("三刻鐘", "45分鐘").replace("一刻鐘", "15分鐘");
        boolean explicit24Hour = normalized.contains(":");
        String numeric = LocalAnswer.replaceChineseNumbers(timer
                ? timerWords : normalized.replace("點鐘", ":").replace("點", ":"));
        return timer ? parseTimer(numeric) : parseAlarm(numeric, explicit24Hour);
    }

    private static ClockRequest parseAlarm(String speech, boolean explicit24Hour) {
        Matcher match = ALARM.matcher(speech);
        if (!match.matches()) return clarify("請說明鬧鐘時間，例如早上七點半。");
        if (match.group(1) != null && match.group(2) != null) {
            return clarify("指定日期的鬧鐘不能同時設定每週重複，請分開設定。");
        }
        if (match.group(1) != null) return clarify("指定日期的鬧鐘目前請在 Android 時鐘裡設定。");
        String period = match.group(3);
        int hour = number(match.group(4));
        int minute = match.group(5) == null ? number(match.group(6)) : 30;
        boolean zeroHour = hour == 0 && ("凌晨".equals(period) || "午夜".equals(period));
        if (hour > 23 || minute > 59 || (period != null && !zeroHour && (hour < 1 || hour > 12))) {
            return clarify("這個時間無法設定，請再確認小時與分鐘。");
        }
        if (period == null && hour >= 1 && hour <= 12 && !explicit24Hour) {
            return clarify("請說明早上或晚上，或使用二十四小時制。");
        }
        if ("中午".equals(period) && hour != 12) {
            return clarify("請用上午、下午或二十四小時制說明時間。");
        }
        if ("晚上".equals(period) && hour == 12) {
            return clarify("請確認是午夜零點，或中午十二點。");
        }
        if ("午夜".equals(period) || zeroHour) {
            hour = 0;
        } else if ("下午".equals(period) || "晚上".equals(period) || "中午".equals(period)) {
            hour = hour % 12 + 12;
        } else if (period != null) {
            hour %= 12;
        }
        return new ClockRequest(Kind.ALARM, hour, minute, 0, repeatDays(match.group(2)), null);
    }

    private static ClockRequest parseTimer(String speech) {
        Matcher match = TIMER.matcher(speech);
        if (!match.matches()) return clarify("請說明倒數時間，例如倒數三分鐘。");
        int seconds = duration(match.group(1), 3600) + duration(match.group(2), 60)
                + duration(match.group(3), 1);
        if (seconds < 1 || seconds > 86400) return clarify("倒數時間需介於一秒到二十四小時。");
        return new ClockRequest(Kind.TIMER, 0, 0, seconds, new ArrayList<>(), null);
    }

    private static ArrayList<Integer> repeatDays(String repeat) {
        ArrayList<Integer> days = new ArrayList<>();
        if (repeat == null) return days;
        if ("每天".equals(repeat) || "每日".equals(repeat)) {
            addDays(days, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
        } else if ("平日".equals(repeat) || "工作日".equals(repeat)) {
            addDays(days, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY);
        } else if ("週末".equals(repeat) || "周末".equals(repeat)) {
            addDays(days, Calendar.SATURDAY, Calendar.SUNDAY);
        } else {
            Matcher range = WEEKDAY_RANGE.matcher(repeat);
            if (range.matches()) {
                int day = weekday(range.group(1).charAt(0));
                int last = weekday(range.group(2).charAt(0));
                while (true) {
                    days.add(day);
                    if (day == last) break;
                    day = day % Calendar.SATURDAY + Calendar.SUNDAY;
                }
            } else {
                days.add(weekday(repeat.charAt(repeat.length() - 1)));
            }
        }
        return days;
    }

    private static int weekday(char day) {
        switch (day) {
            case '一': case '1': return Calendar.MONDAY;
            case '二': case '2': return Calendar.TUESDAY;
            case '三': case '3': return Calendar.WEDNESDAY;
            case '四': case '4': return Calendar.THURSDAY;
            case '五': case '5': return Calendar.FRIDAY;
            case '六': case '6': return Calendar.SATURDAY;
            case '日': case '天': case '7': return Calendar.SUNDAY;
            default: throw new IllegalArgumentException("Unsupported weekday");
        }
    }

    private static void addDays(ArrayList<Integer> days, int... values) {
        for (int value : values) days.add(value);
    }

    private static int number(String text) {
        return text == null ? 0 : Integer.parseInt(text);
    }

    private static int duration(String text, int unitSeconds) {
        if (text == null) return 0;
        if ("半".equals(text)) return unitSeconds / 2;
        return number(text) * unitSeconds;
    }

    private static ClockRequest clarify(String prompt) {
        return new ClockRequest(Kind.CLARIFICATION, 0, 0, 0, new ArrayList<>(), prompt);
    }
}
