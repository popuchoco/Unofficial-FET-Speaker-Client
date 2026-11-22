package community.speaker.client;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClockRequestTest {
    @Test public void recognizesSpokenAnd24HourAlarms() {
        assertAlarm("幫我設個早上七點半的鬧鐘", 7, 30);
        assertAlarm("晚上十一點鐘鬧鐘", 23, 0);
        assertAlarm("中午十二點鬧鐘", 12, 0);
        assertAlarm("凌晨十二點叫醒我", 0, 0);
        assertAlarm("午夜十二點半鬧鐘", 0, 30);
        assertAlarm("午夜零點鬧鐘", 0, 0);
        assertAlarm("凌晨零點半鬧鐘", 0, 30);
        assertAlarm("設定鬧鐘18:05", 18, 5);
        assertAlarm("設定鬧鐘08:10", 8, 10);
        assertAlarm("請問幫我設個早上七點鬧鐘", 7, 0);
    }

    @Test public void asksAboutUnclearOrUnsupportedAlarms() {
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("七點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("晚上十二點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("設定明天早上七點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("設定鬧鐘25:00").kind);
    }

    @Test public void recognizesRepeatingDailyWeekdayWeekendAndWeeklyAlarms() {
        assertAlarmDays("每天早上七點鬧鐘", Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
        assertAlarmDays("設個平日早上七點半的鬧鐘", Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY);
        assertAlarmDays("週末早上九點鬧鐘", Calendar.SATURDAY, Calendar.SUNDAY);
        assertAlarmDays("每週星期一晚上九點鬧鐘", Calendar.MONDAY);
        assertAlarmDays("每週一到週五早上七點鬧鐘", Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY);
        assertAlarmDays("每日08:10叫醒我", Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
    }

    @Test public void keepsSpecificDateAlarmsSeparateFromRepeatingSchedules() {
        assertEquals(ClockRequest.Kind.CLARIFICATION,
                ClockRequest.parse("明天每天早上七點鬧鐘").kind);
    }

    @Test public void recognizesCompoundTimersWithinAndroidRange() {
        assertTimer("開始倒數一小時兩分鐘三秒", 3723);
        assertTimer("五分鐘計時", 300);
        assertTimer("倒數二十四小時", 86400);
        assertTimer("倒數半小時", 1800);
        assertTimer("設定半個小時倒數", 1800);
        assertTimer("倒數半分鐘", 30);
        assertTimer("倒數一刻鐘", 900);
        assertTimer("三刻鐘計時", 2700);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("倒數二十五小時").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("倒數零秒").kind);
        assertNull(ClockRequest.parse("現在幾點"));
    }

    private static void assertAlarm(String input, int hour, int minute) {
        ClockRequest request = ClockRequest.parse(input);
        assertEquals(input, ClockRequest.Kind.ALARM, request.kind);
        assertEquals(hour, request.hour);
        assertEquals(minute, request.minute);
    }

    private static void assertTimer(String input, int seconds) {
        ClockRequest request = ClockRequest.parse(input);
        assertEquals(ClockRequest.Kind.TIMER, request.kind);
        assertEquals(seconds, request.seconds);
    }

    private static void assertAlarmDays(String input, Integer... expectedDays) {
        ClockRequest request = ClockRequest.parse(input);
        assertEquals(input, ClockRequest.Kind.ALARM, request.kind);
        assertEquals(new ArrayList<>(Arrays.asList(expectedDays)), request.repeatDays);
    }
}
