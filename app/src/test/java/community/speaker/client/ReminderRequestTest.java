package community.speaker.client;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
