package community.speaker.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReminderRequest {
    private static final String WEEKDAY = "[一二三四五六日天1-7]";
    private static final String RANGE = "每(?:週|周|星期|禮拜)?(?:星期|禮拜)?" + WEEKDAY
            + "(?:到|至)(?:週|周|星期|禮拜)?" + WEEKDAY;
    private static final String REPEAT = "(?:每天|每日|平日|工作日|週末|周末|"
            + "每(?:週|周|星期|禮拜)(?:星期|禮拜)?" + WEEKDAY + "|" + RANGE + ")";
    private static final Pattern WEEKDAY_RANGE = Pattern.compile(
            "每(?:週|周|星期|禮拜)?(?:星期|禮拜)?(" + WEEKDAY + ")(?:到|至)"
                    + "(?:週|周|星期|禮拜)?(" + WEEKDAY + ")");
    private static final Pattern REQUEST = Pattern.compile(
            "(?:請幫我|幫我|請)?(.*?)後\\s*提醒我\\s*(.+)", Pattern.DOTALL);
    private static final Pattern SCHEDULED = Pattern.compile(
            "(?:請幫我|幫我|請)?(今天|明天|後天|下(?:週|周|星期|禮拜)(?:星期|禮拜)?" + WEEKDAY
                    + "|(?:星期|禮拜)" + WEEKDAY + "|" + REPEAT + ")"
                    + "(凌晨|早上|上午|中午|下午|晚上)?"
                    + "([0-9]{1,2})(?:點鐘?|時)(半|[0-9]{1,2}分)?提醒我(.+)");
    final int seconds;
    final long dueAtMillis;
    final String message;
    final List<Integer> repeatDays;
    final int scheduleHour;
    final int scheduleMinute;

    private ReminderRequest(int seconds, long dueAtMillis, String message, List<Integer> repeatDays,
                            int scheduleHour, int scheduleMinute) {
        this.seconds = seconds;
        this.dueAtMillis = dueAtMillis;
        this.message = message;
        this.repeatDays = new ArrayList<>(repeatDays);
        this.scheduleHour = scheduleHour;
        this.scheduleMinute = scheduleMinute;
    }

    static ReminderRequest parse(String speech) {
        return parse(speech, System.currentTimeMillis(), TimeZone.getDefault());
    }

    static ReminderRequest parse(String speech, long nowMillis, TimeZone timeZone) {
        if (!speech.contains("提醒我")) return null;
        String normalized = SpeechPrefix.strip(Normalizer.normalize(speech.trim(), Normalizer.Form.NFKC));
        Matcher match = REQUEST.matcher(normalized);
        if (match.matches()) {
            String message = match.group(2).replaceFirst("[。！？!?]+$", "").trim();
            ClockRequest duration = ClockRequest.parse("倒數" + match.group(1));
            if (duration != null && duration.kind == ClockRequest.Kind.TIMER
                    && !message.isEmpty() && message.length() <= 200) {
                return new ReminderRequest(duration.seconds, 0, message, new ArrayList<>(), -1, -1);
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
            String dateTerm = scheduled.group(1);
            List<Integer> repeatDays = repeatDays(dateTerm);
            if (!repeatDays.isEmpty()) {
                long dueAtMillis = ReminderSchedule.nextDue(nowMillis, repeatDays, hour, minute, timeZone);
                return new ReminderRequest(0, dueAtMillis, message, repeatDays, hour, minute);
            }
            if ("今天".equals(dateTerm)) due.add(Calendar.DAY_OF_YEAR, 0);
            else if ("明天".equals(dateTerm)) due.add(Calendar.DAY_OF_YEAR, 1);
            else if ("後天".equals(dateTerm)) due.add(Calendar.DAY_OF_YEAR, 2);
            else scheduleWeekday(due, dateTerm);
            due.set(Calendar.HOUR_OF_DAY, hour);
            due.set(Calendar.MINUTE, minute);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);
            due.setLenient(false);
            try {
                long dueAtMillis = due.getTimeInMillis();
                if (dueAtMillis <= nowMillis) {
                    if (isWeekday(dateTerm) && !isNextWeek(dateTerm)) {
                        due.add(Calendar.DAY_OF_YEAR, 7);
                        dueAtMillis = due.getTimeInMillis();
                    } else {
                        return invalid();
                    }
                }
                return new ReminderRequest(0, dueAtMillis, message, repeatDays, hour, minute);
            } catch (IllegalArgumentException invalidTime) {
                return invalid();
            }
        }
        return invalid();
    }

    private static ReminderRequest invalid() {
        return new ReminderRequest(0, 0, "", new ArrayList<>(), -1, -1);
    }

    private static List<Integer> repeatDays(String term) {
        List<Integer> days = new ArrayList<>();
        if ("每天".equals(term) || "每日".equals(term)) {
            addDays(days, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
        } else if ("平日".equals(term) || "工作日".equals(term)) {
            addDays(days, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY);
        } else if ("週末".equals(term) || "周末".equals(term)) {
            addDays(days, Calendar.SATURDAY, Calendar.SUNDAY);
        } else if (term != null) {
            Matcher range = WEEKDAY_RANGE.matcher(term);
            if (range.matches()) {
                int day = calendarWeekday(range.group(1).charAt(range.group(1).length() - 1));
                int last = calendarWeekday(range.group(2).charAt(range.group(2).length() - 1));
                while (true) {
                    days.add(day);
                    if (day == last) break;
                    day = day % Calendar.SATURDAY + Calendar.SUNDAY;
                }
            } else if (term.startsWith("每")) {
                days.add(calendarWeekday(term.charAt(term.length() - 1)));
            }
        }
        return days;
    }

    private static int calendarWeekday(char day) {
        switch (day) {
            case '日': case '天': case '7': return Calendar.SUNDAY;
            case '一': case '1': return Calendar.MONDAY;
            case '二': case '2': return Calendar.TUESDAY;
            case '三': case '3': return Calendar.WEDNESDAY;
            case '四': case '4': return Calendar.THURSDAY;
            case '五': case '5': return Calendar.FRIDAY;
            case '六': case '6': return Calendar.SATURDAY;
            default: throw new IllegalArgumentException("Unsupported weekday");
        }
    }

    private static void addDays(List<Integer> days, int... values) {
        for (int value : values) days.add(value);
    }

    private static void scheduleWeekday(Calendar due, String dateTerm) {
        int targetDay = weekdayIndex(dateTerm.charAt(dateTerm.length() - 1));
        int currentDay = (due.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int offset;
        if (isNextWeek(dateTerm)) {
            offset = 7 - currentDay + targetDay;
        } else {
            offset = (targetDay - currentDay + 7) % 7;
        }
        due.add(Calendar.DAY_OF_YEAR, offset);
    }

    private static boolean isWeekday(String dateTerm) {
        return dateTerm.startsWith("下") || dateTerm.startsWith("星期") || dateTerm.startsWith("禮拜");
    }

    private static boolean isNextWeek(String dateTerm) {
        return dateTerm.startsWith("下週") || dateTerm.startsWith("下周")
                || dateTerm.startsWith("下星期") || dateTerm.startsWith("下禮拜");
    }

    private static int weekdayIndex(char day) {
        switch (day) {
            case '一': return 0;
            case '二': return 1;
            case '三': return 2;
            case '四': return 3;
            case '五': return 4;
            case '六': return 5;
            case '日': case '天': return 6;
            case '1': return 0;
            case '2': return 1;
            case '3': return 2;
            case '4': return 3;
            case '5': return 4;
            case '6': return 5;
            case '7': return 6;
            default: throw new IllegalArgumentException("Unsupported weekday");
        }
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
