package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClockRequest {
    enum Kind { ALARM, TIMER, CLARIFICATION }

    private static final Pattern ALARM = Pattern.compile(
            "(?:設定|設置|設個)?(?:鬧鐘|叫醒我)?(今天|明天)?(凌晨|午夜|早上|上午|中午|下午|晚上)?"
                    + "([0-9]{1,2})(?:點鐘?|:)(?:(半)|([0-9]{1,2})(?:分(?:鐘)?)?)?"
                    + "(?:的)?(?:鬧鐘|叫醒我)?");
    private static final Pattern TIMER = Pattern.compile(
            "(?:設定|設置|開始)?(?:倒數|計時器?)?(?:([0-9]{1,5}|半)(?:個)?小時)?"
                    + "(?:([0-9]{1,5}|半)分鐘?)?(?:([0-9]{1,5})秒(?:鐘)?)?(?:的)?(?:倒數|計時器?)?");
    final Kind kind;
    final int hour;
    final int minute;
    final int seconds;
    final String prompt;

    private ClockRequest(Kind kind, int hour, int minute, int seconds, String prompt) {
        this.kind = kind;
        this.hour = hour;
        this.minute = minute;
        this.seconds = seconds;
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
        String numeric = LocalAnswer.replaceChineseNumbers(timer
                ? timerWords : normalized.replace("點鐘", ":").replace("點", ":"));
        return timer ? parseTimer(numeric) : parseAlarm(numeric);
    }

    private static ClockRequest parseAlarm(String speech) {
        Matcher match = ALARM.matcher(speech);
        if (!match.matches()) return clarify("請說明鬧鐘時間，例如早上七點半。");
        if (match.group(1) != null) return clarify("指定日期的鬧鐘目前請在 Android 時鐘裡設定。");
        String period = match.group(2);
        int hour = number(match.group(3));
        int minute = match.group(4) == null ? number(match.group(5)) : 30;
        boolean zeroHour = hour == 0 && ("凌晨".equals(period) || "午夜".equals(period));
        if (hour > 23 || minute > 59 || (period != null && !zeroHour && (hour < 1 || hour > 12))) {
            return clarify("這個時間無法設定，請再確認小時與分鐘。");
        }
        if (period == null && hour >= 1 && hour <= 12) {
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
        return new ClockRequest(Kind.ALARM, hour, minute, 0, null);
    }

    private static ClockRequest parseTimer(String speech) {
        Matcher match = TIMER.matcher(speech);
        if (!match.matches()) return clarify("請說明倒數時間，例如倒數三分鐘。");
        int seconds = duration(match.group(1), 3600) + duration(match.group(2), 60)
                + duration(match.group(3), 1);
        if (seconds < 1 || seconds > 86400) return clarify("倒數時間需介於一秒到二十四小時。");
        return new ClockRequest(Kind.TIMER, 0, 0, seconds, null);
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
        return new ClockRequest(Kind.CLARIFICATION, 0, 0, 0, prompt);
    }
}
