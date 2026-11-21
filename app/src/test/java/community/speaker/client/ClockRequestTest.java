package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClockRequestTest {
    @Test public void recognizesSpokenAnd24HourAlarms() {
        assertAlarm("幫我設個早上七點半的鬧鐘", 7, 30);
        assertAlarm("晚上十一點鐘鬧鐘", 23, 0);
        assertAlarm("中午十二點鬧鐘", 12, 0);
        assertAlarm("凌晨十二點叫醒我", 0, 0);
        assertAlarm("設定鬧鐘18:05", 18, 5);
    }

    @Test public void asksAboutUnclearOrUnsupportedAlarms() {
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("七點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("晚上十二點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("設定明天早上七點鬧鐘").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("設定鬧鐘25:00").kind);
    }

    @Test public void recognizesCompoundTimersWithinAndroidRange() {
        assertTimer("開始倒數一小時兩分鐘三秒", 3723);
        assertTimer("五分鐘計時", 300);
        assertTimer("倒數二十四小時", 86400);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("倒數二十五小時").kind);
        assertEquals(ClockRequest.Kind.CLARIFICATION, ClockRequest.parse("倒數零秒").kind);
        assertNull(ClockRequest.parse("現在幾點"));
    }

    private static void assertAlarm(String input, int hour, int minute) {
        ClockRequest request = ClockRequest.parse(input);
        assertEquals(ClockRequest.Kind.ALARM, request.kind);
        assertEquals(hour, request.hour);
        assertEquals(minute, request.minute);
    }

    private static void assertTimer(String input, int seconds) {
        ClockRequest request = ClockRequest.parse(input);
        assertEquals(ClockRequest.Kind.TIMER, request.kind);
        assertEquals(seconds, request.seconds);
    }
}
