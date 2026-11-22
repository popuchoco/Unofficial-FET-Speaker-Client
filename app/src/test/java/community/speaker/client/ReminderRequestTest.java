package community.speaker.client;

import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReminderRequestTest {
    @Test public void parsesDurationAndPreservesMessage() {
        ReminderRequest request = ReminderRequest.parse("幫我三分鐘後提醒我 關火！");
        assertEquals(180, request.seconds);
        assertEquals("關火", request.message);

        request = ReminderRequest.parse("一小時兩分鐘後提醒我拿快遞");
        assertEquals(3720, request.seconds);
        assertEquals("拿快遞", request.message);

        request = ReminderRequest.parse("半小時後提醒我關火");
        assertEquals(1800, request.seconds);
        assertEquals("關火", request.message);

        request = ReminderRequest.parse("一刻鐘後提醒我拿包裹");
        assertEquals(900, request.seconds);
        assertEquals("拿包裹", request.message);

        request = ReminderRequest.parse("請問一下三分鐘後提醒我關火");
        assertEquals(180, request.seconds);
    }

    @Test public void requiresActionAndDuration() {
        assertEquals(0, ReminderRequest.parse("三分鐘後提醒我").seconds);
        assertEquals(0, ReminderRequest.parse("明天提醒我拿包裹").seconds);
        assertEquals(0, ReminderRequest.parse("二十五小時後提醒我拿包裹").seconds);
        assertEquals(0, ReminderRequest.parse("三分鐘後提醒我。").seconds);
        assertNull(ReminderRequest.parse("倒數三分鐘"));
    }

    @Test public void schedulesTomorrowAtTheSpokenLocalTime() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 22, 10, 0);

        ReminderRequest request = ReminderRequest.parse("明天早上九點半提醒我拿包裹", now.getTimeInMillis(), zone);
        Calendar expected = (Calendar) now.clone();
        expected.add(Calendar.DAY_OF_YEAR, 1);
        expected.set(Calendar.HOUR_OF_DAY, 9);
        expected.set(Calendar.MINUTE, 30);

        assertEquals(expected.getTimeInMillis(), request.dueAtMillis);
        assertEquals("拿包裹", request.message);
        assertEquals(9, request.scheduleHour);
        assertTrue(request.repeatDays.isEmpty());
    }

    @Test public void schedulesUpcomingAndNextWeekWeekdays() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 24, 10, 0);

        ReminderRequest upcoming = ReminderRequest.parse("星期五早上九點提醒我拿包裹",
                now.getTimeInMillis(), zone);
        Calendar expectedUpcoming = (Calendar) now.clone();
        expectedUpcoming.add(Calendar.DAY_OF_YEAR, 3);
        expectedUpcoming.set(Calendar.HOUR_OF_DAY, 9);
        expectedUpcoming.set(Calendar.MINUTE, 0);
        assertEquals(expectedUpcoming.getTimeInMillis(), upcoming.dueAtMillis);

        ReminderRequest nextWeek = ReminderRequest.parse("下週星期五早上九點提醒我繳費",
                now.getTimeInMillis(), zone);
        Calendar expectedNextWeek = (Calendar) expectedUpcoming.clone();
        expectedNextWeek.add(Calendar.DAY_OF_YEAR, 7);
        assertEquals(expectedNextWeek.getTimeInMillis(), nextWeek.dueAtMillis);
        assertEquals("繳費", nextWeek.message);

        ReminderRequest nextSunday = ReminderRequest.parse("下週星期日早上九點提醒我休息",
                now.getTimeInMillis(), zone);
        Calendar expectedSunday = (Calendar) now.clone();
        expectedSunday.add(Calendar.DAY_OF_YEAR, 12);
        expectedSunday.set(Calendar.HOUR_OF_DAY, 9);
        assertEquals(expectedSunday.getTimeInMillis(), nextSunday.dueAtMillis);
    }

    @Test public void rollsPastWeekdayTimesToTheFollowingWeek() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 22, 10, 0);

        ReminderRequest request = ReminderRequest.parse("星期日早上九點提醒我休息",
                now.getTimeInMillis(), zone);
        Calendar expected = (Calendar) now.clone();
        expected.add(Calendar.DAY_OF_YEAR, 7);
        expected.set(Calendar.HOUR_OF_DAY, 9);
        expected.set(Calendar.MINUTE, 0);
        assertEquals(expected.getTimeInMillis(), request.dueAtMillis);
    }

    @Test public void schedulesDailyAndWeeklyRepeatingReminders() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        Calendar now = Calendar.getInstance(zone);
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 22, 10, 0);

        ReminderRequest daily = ReminderRequest.parse("每天早上十一點提醒我關火",
                now.getTimeInMillis(), zone);
        assertEquals(Arrays.asList(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
                daily.repeatDays);
        Calendar expectedDaily = (Calendar) now.clone();
        expectedDaily.set(Calendar.HOUR_OF_DAY, 11);
        expectedDaily.set(Calendar.MINUTE, 0);
        assertEquals(expectedDaily.getTimeInMillis(), daily.dueAtMillis);
        assertEquals(11, daily.scheduleHour);
        assertEquals("關火", daily.message);

        ReminderRequest weekdays = ReminderRequest.parse("平日早上九點提醒我開會",
                now.getTimeInMillis(), zone);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY), weekdays.repeatDays);
        Calendar expectedWeekday = (Calendar) now.clone();
        expectedWeekday.add(Calendar.DAY_OF_YEAR, 1);
        expectedWeekday.set(Calendar.HOUR_OF_DAY, 9);
        assertEquals(expectedWeekday.getTimeInMillis(), weekdays.dueAtMillis);

        ReminderRequest weekly = ReminderRequest.parse("每週星期一晚上九點提醒我繳費",
                now.getTimeInMillis(), zone);
        assertEquals(Arrays.asList(Calendar.MONDAY), weekly.repeatDays);
        assertEquals("每週星期一", ReminderSchedule.describe(weekly.repeatDays));

        ReminderRequest range = ReminderRequest.parse("每週一到週五早上七點提醒我打卡",
                now.getTimeInMillis(), zone);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY), range.repeatDays);
    }

    @Test public void requiresAnUnambiguousTimeForRepeatingReminders() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
        long now = 1795312800000L;
        assertEquals(0, ReminderRequest.parse("每天九點提醒我關火", now, zone).dueAtMillis);
        assertEquals(0, ReminderRequest.parse("明天每天早上九點提醒我關火", now, zone).dueAtMillis);
    }
}
