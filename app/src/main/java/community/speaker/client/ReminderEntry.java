package community.speaker.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ReminderEntry {
    private static final String VERSION_2 = "v2\n";

    final String id;
    final long due;
    final String message;
    final List<Integer> repeatDays;
    final int scheduleHour;
    final int scheduleMinute;

    ReminderEntry(String id, long due, String message, List<Integer> repeatDays,
                  int scheduleHour, int scheduleMinute) {
        this.id = id;
        this.due = due;
        this.message = message;
        this.repeatDays = new ArrayList<>(repeatDays);
        this.scheduleHour = scheduleHour;
        this.scheduleMinute = scheduleMinute;
    }

    boolean isRepeating() {
        return !repeatDays.isEmpty();
    }

    String encode() {
        StringBuilder days = new StringBuilder();
        for (int day : repeatDays) {
            if (days.length() > 0) days.append(',');
            days.append(day);
        }
        return VERSION_2 + due + "\n" + days + "\n" + scheduleHour + "\n"
                + scheduleMinute + "\n" + message;
    }

    static ReminderEntry decode(String id, String saved) {
        if (saved == null) return null;
        if (!saved.startsWith(VERSION_2)) {
            int separator = saved.indexOf('\n');
            if (separator < 0) return null;
            try {
                long due = Long.parseLong(saved.substring(0, separator));
                if (due <= 0) return null;
                return new ReminderEntry(id, due, saved.substring(separator + 1),
                        new ArrayList<>(), -1, -1);
            } catch (NumberFormatException invalid) {
                return null;
            }
        }

        String[] fields = saved.split("\n", 6);
        if (fields.length < 6) return null;
        try {
            long due = Long.parseLong(fields[1]);
            List<Integer> repeatDays = parseDays(fields[2]);
            int hour = Integer.parseInt(fields[3]);
            int minute = Integer.parseInt(fields[4]);
            boolean unsetTime = hour == -1 && minute == -1;
            boolean validTime = hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
            if (due <= 0 || repeatDays == null || (!unsetTime && !validTime)
                    || (!repeatDays.isEmpty() && !validTime)) return null;
            return new ReminderEntry(id, due, fields[5], repeatDays, hour, minute);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static List<Integer> parseDays(String saved) {
        List<Integer> days = new ArrayList<>();
        if (saved.isEmpty()) return days;
        for (String value : saved.split(Pattern.quote(","))) {
            try {
                int day = Integer.parseInt(value);
                if (day < 1 || day > 7 || days.contains(day)) return null;
                days.add(day);
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return days;
    }
}
