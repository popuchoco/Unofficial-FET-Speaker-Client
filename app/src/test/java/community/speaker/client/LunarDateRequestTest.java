package community.speaker.client;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class LunarDateRequestTest {
    private final Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));

    public LunarDateRequestTest() {
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 21, 19, 5);
    }

    @Test public void parsesSpecificGregorianDatesForLunarLookup() {
        LunarDateRequest full = LunarDateRequest.parse("2026年11月22日的農曆日期", now);
        assertNotNull(full);
        assertEquals(2026, full.year);
        assertEquals(11, full.month);
        assertEquals(22, full.day);
        assertNull(full.error);

        LunarDateRequest currentYear = LunarDateRequest.parse("二月二十九日農曆是幾日", now);
        assertNotNull(currentYear);
        assertEquals(2026, currentYear.year);
        assertEquals(2, currentYear.month);
        assertEquals(29, currentYear.day);
        assertEquals("這個日期不存在，請再確認年月日。", currentYear.error);

        LunarDateRequest nextYear = LunarDateRequest.parse("明年二月二十九日農曆", now);
        assertNotNull(nextYear);
        assertEquals(2027, nextYear.year);
        assertEquals("這個日期不存在，請再確認年月日。", nextYear.error);
    }

    @Test public void leavesTodayAndUnrelatedRequestsForExistingHandlers() {
        assertNull(LunarDateRequest.parse("今天農曆幾月幾日", now));
        assertNull(LunarDateRequest.parse("2026年11月22日星期幾", now));
        assertNull(LunarDateRequest.parse("明天天氣如何", now));
    }
}
