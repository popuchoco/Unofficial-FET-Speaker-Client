package community.speaker.client;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

final class ReminderSchedule {
    private ReminderSchedule() {}

    static long nextDue(long afterMillis, List<Integer> repeatDays, int hour, int minute,
                        TimeZone timeZone) {
        if (repeatDays == null || repeatDays.isEmpty() || hour < 0 || hour > 23
                || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("A repeating reminder needs weekdays and a valid time");
        }
        Calendar base = Calendar.getInstance(timeZone);
        base.setTimeInMillis(afterMillis);
        for (int offset = 0; offset <= 7; offset++) {
            Calendar candidate = (Calendar) base.clone();
            candidate.add(Calendar.DAY_OF_YEAR, offset);
            candidate.set(Calendar.HOUR_OF_DAY, hour);
            candidate.set(Calendar.MINUTE, minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            if (repeatDays.contains(candidate.get(Calendar.DAY_OF_WEEK))
                    && candidate.getTimeInMillis() > afterMillis) {
                return candidate.getTimeInMillis();
            }
        }
        throw new IllegalArgumentException("No valid weekday in repeating reminder");
    }

    static String describe(List<Integer> repeatDays) {
        if (repeatDays == null || repeatDays.isEmpty()) return "";
        List<Integer> weekdays = days(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY);
        List<Integer> weekend = days(Calendar.SATURDAY, Calendar.SUNDAY);
        List<Integer> everyDay = days(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
        if (sameDays(repeatDays, everyDay)) return "每天";
        if (sameDays(repeatDays, weekdays)) return "平日";
        if (sameDays(repeatDays, weekend)) return "週末";

        StringBuilder label = new StringBuilder("每週");
        int[] ordered = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        boolean first = true;
        for (int index = 0; index < ordered.length; index++) {
            if (!repeatDays.contains(ordered[index])) continue;
            if (!first) label.append("、");
            label.append(names[index]);
            first = false;
        }
        return first ? "" : label.toString();
    }

    private static List<Integer> days(int... values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) result.add(value);
        return result;
    }

    private static boolean sameDays(List<Integer> actual, List<Integer> expected) {
        return actual.size() == expected.size() && actual.containsAll(expected);
    }
}
