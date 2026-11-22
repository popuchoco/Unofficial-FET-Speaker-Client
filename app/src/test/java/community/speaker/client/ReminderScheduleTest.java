package community.speaker.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class ReminderScheduleTest {
    @Test public void picksTheNextMatchingLocalWeekdayAndSkipsPastTimes() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 22, 10, 0);

        long monday = ReminderSchedule.nextDue(now.getTimeInMillis(),
                Arrays.asList(Calendar.MONDAY, Calendar.WEDNESDAY), 9, 0, zone);
        Calendar expected = (Calendar) now.clone();
        expected.add(Calendar.DAY_OF_YEAR, 1);
        expected.set(Calendar.HOUR_OF_DAY, 9);
        assertEquals(expected.getTimeInMillis(), monday);

        long daily = ReminderSchedule.nextDue(now.getTimeInMillis(),
                Arrays.asList(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
                9, 0, zone);
        assertEquals(expected.getTimeInMillis(), daily);
    }

    @Test public void labelsCommonAndCustomRepeatSchedules() {
        assertEquals("每天", ReminderSchedule.describe(Arrays.asList(Calendar.SUNDAY, Calendar.MONDAY,
                Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                Calendar.SATURDAY)));
        assertEquals("平日", ReminderSchedule.describe(Arrays.asList(Calendar.MONDAY,
                Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)));
        assertEquals("週末", ReminderSchedule.describe(Arrays.asList(Calendar.SATURDAY, Calendar.SUNDAY)));
        assertEquals("每週星期一、星期三", ReminderSchedule.describe(
                Arrays.asList(Calendar.MONDAY, Calendar.WEDNESDAY)));
    }
}
