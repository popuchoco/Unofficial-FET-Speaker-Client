package community.speaker.client;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReminderRequest {
    private static final Pattern REQUEST = Pattern.compile(
            "(?:請幫我|幫我|請)?(.*?)後\\s*提醒我\\s*(.+)", Pattern.DOTALL);
    private static final Pattern SCHEDULED = Pattern.compile(
            "(?:請幫我|幫我|請)?(今天|明天|後天)(凌晨|早上|上午|中午|下午|晚上)?"
                    + "([0-9]{1,2})(?:點鐘?|時)(半|[0-9]{1,2}分)?提醒我(.+)");
    final int seconds;
    final long dueAtMillis;
    final String message;

    private ReminderRequest(int seconds, long dueAtMillis, String message) {
        this.seconds = seconds;
        this.dueAtMillis = dueAtMillis;
        this.message = message;
    }

    static ReminderRequest parse(String speech) {
        return parse(speech, System.currentTimeMillis(), TimeZone.getDefault());
    }

    static ReminderRequest parse(String speech, long nowMillis, TimeZone timeZone) {
        if (!speech.contains("提醒我")) return null;
        String normalized = Normalizer.normalize(speech.trim(), Normalizer.Form.NFKC);
        Matcher match = REQUEST.matcher(normalized);
        if (match.matches()) {
            String message = match.group(2).replaceFirst("[。！？!?]+$", "").trim();
            ClockRequest duration = ClockRequest.parse("倒數" + match.group(1));
            if (duration != null && duration.kind == ClockRequest.Kind.TIMER
                    && !message.isEmpty() && message.length() <= 200) {
                return new ReminderRequest(duration.seconds, 0, message);
            }
        }
        String compact = normalized.replaceAll("[\\s\\p{Z}，。！？?!,]", "");
        String scheduledInput = LocalAnswer.replaceChineseNumbers(
                replaceNumberBefore(compact, "點").replace("點半", "點30分"));
        Matcher scheduled = SCHEDULED.matcher(scheduledInput);
        if (scheduled.matches()) {
            String message = scheduled.group(5).trim();
            int hour = Integer.parseInt(scheduled.group(3));
            String period = scheduled.group(2);
            int minute = "半".equals(scheduled.group(4)) ? 30
                    : scheduled.group(4) == null ? 0
                    : Integer.parseInt(scheduled.group(4).replace("分", ""));
            if (message.isEmpty() || message.length() > 200 || minute > 59 || hour > 23) {
                return invalid();
            }
            if (period != null && (hour < 1 || hour > 12)) return invalid();
            if (period == null && hour >= 1 && hour <= 12) return invalid();
            if ("中午".equals(period) && hour != 12) return invalid();
            if ("凌晨".equals(period) || "早上".equals(period) || "上午".equals(period)) {
                hour %= 12;
            } else if ("下午".equals(period) || "晚上".equals(period)) {
                hour = hour % 12 + 12;
            }

            Calendar due = Calendar.getInstance(timeZone);
            due.setTimeInMillis(nowMillis);
            int dayOffset = "今天".equals(scheduled.group(1)) ? 0
                    : "明天".equals(scheduled.group(1)) ? 1 : 2;
            due.add(Calendar.DAY_OF_YEAR, dayOffset);
            due.set(Calendar.HOUR_OF_DAY, hour);
            due.set(Calendar.MINUTE, minute);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);
            due.setLenient(false);
            try {
                long dueAtMillis = due.getTimeInMillis();
                if (dueAtMillis <= nowMillis) return invalid();
                return new ReminderRequest(0, dueAtMillis, message);
            } catch (IllegalArgumentException invalidTime) {
                return invalid();
            }
        }
        return invalid();
    }

    private static ReminderRequest invalid() {
        return new ReminderRequest(0, 0, "");
    }

    private static String replaceNumberBefore(String input, String unit) {
        Matcher number = Pattern.compile("([零〇一二兩三四五六七八九十]+)(?="
                + Pattern.quote(unit) + ")").matcher(input);
        StringBuffer result = new StringBuffer();
        while (number.find()) {
            number.appendReplacement(result, LocalAnswer.replaceChineseNumbers(number.group(1)));
        }
        number.appendTail(result);
        return result.toString();
    }
}
