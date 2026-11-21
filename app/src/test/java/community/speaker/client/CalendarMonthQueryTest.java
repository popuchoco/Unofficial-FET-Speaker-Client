package community.speaker.client;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CalendarMonthQueryTest {
    private Calendar date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar;
    }

    @Test public void answersDaysInExplicitAndCurrentYearMonths() {
        Calendar now = date(2026, 11, 21);
        assertEquals("2024年2月有29天。", CalendarMonthQuery.answer("2024年2月有幾天", now));
        assertEquals("2025年2月有28天。", CalendarMonthQuery.answer("2025年2月份天數", now));
        assertEquals("2026年2月有28天。", CalendarMonthQuery.answer("二月多少天", now));
    }

    @Test public void answersRelativeMonthsAcrossYearBoundaries() {
        Calendar now = date(2026, 11, 21);
        assertEquals("2026年12月有31天。", CalendarMonthQuery.answer("下個月有幾天", now));
        assertEquals("2026年10月有31天。", CalendarMonthQuery.answer("上月多少天", now));
        assertEquals("2027年1月有31天。", CalendarMonthQuery.answer("下月天數", date(2026, 12, 31)));
        assertEquals("2026年11月有30天。", CalendarMonthQuery.answer("上個月幾天", date(2026, 12, 31)));
    }

    @Test public void acceptsCalendarCommandsAndClarifiesMissingOrInvalidMonth() {
        Calendar now = date(2026, 11, 21);
        assertEquals("2027年2月有28天。", CalendarMonthQuery.answer("查萬年曆明年二月有幾天", now));
        assertEquals("2024年2月29日是星期四。",
                CalendarMonthQuery.answer("查萬年曆2024年2月29日星期幾", now));
        assertEquals("今天是2026年11月21日星期六。", CalendarMonthQuery.answer("萬年曆今天幾號", now));
        assertEquals("請指定要查詢的年月，例如「2024年2月有幾天」。", CalendarMonthQuery.answer("打開萬年曆", now));
        assertEquals("請提供有效的年份與月份。", CalendarMonthQuery.answer("2026年13月幾天", now));
        assertNull(CalendarMonthQuery.answer("今天星期幾", now));
    }
}
