package community.speaker.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReminderEntryTest {
    @Test public void roundTripsRecurringSchedulesAndMessages() {
        ReminderEntry original = new ReminderEntry("daily", 1_800_000_000_000L, "帶雨傘",
                Arrays.asList(Calendar.MONDAY, Calendar.WEDNESDAY), 8, 30);

        ReminderEntry restored = ReminderEntry.decode(original.id, original.encode());

        assertEquals(original.due, restored.due);
        assertEquals(original.message, restored.message);
        assertEquals(original.repeatDays, restored.repeatDays);
        assertEquals(original.scheduleHour, restored.scheduleHour);
        assertEquals(original.scheduleMinute, restored.scheduleMinute);
        assertTrue(restored.isRepeating());
    }

    @Test public void keepsExistingOneTimeReminderRecordsReadable() {
        ReminderEntry restored = ReminderEntry.decode("old", "1800000000000\n拿包裹");

        assertEquals(1_800_000_000_000L, restored.due);
        assertEquals("拿包裹", restored.message);
        assertTrue(restored.repeatDays.isEmpty());
        assertEquals(-1, restored.scheduleHour);
        assertEquals(-1, restored.scheduleMinute);
        assertFalse(restored.isRepeating());
    }

    @Test public void roundTripsSingleScheduledRemindersWithTheirSpokenLocalTime() {
        ReminderEntry original = new ReminderEntry("single", 1_800_000_000_000L, "拿包裹",
                java.util.Collections.emptyList(), 9, 30);

        ReminderEntry restored = ReminderEntry.decode(original.id, original.encode());

        assertFalse(restored.isRepeating());
        assertEquals(9, restored.scheduleHour);
        assertEquals(30, restored.scheduleMinute);
        assertEquals(original.due, restored.due);
    }

    @Test public void rejectsMalformedOrImpossibleRepeatSchedules() {
        assertNull(ReminderEntry.decode("bad", "v2\n1\n1,1\n8\n0\n每天提醒"));
        assertNull(ReminderEntry.decode("bad", "v2\n1\n2\n-1\n-1\n每週一提醒"));
        assertNull(ReminderEntry.decode("bad", "not-a-date\n提醒"));
    }
}
