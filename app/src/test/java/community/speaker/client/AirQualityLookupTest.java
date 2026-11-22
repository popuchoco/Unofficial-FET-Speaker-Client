package community.speaker.client;

import org.json.JSONException;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class AirQualityLookupTest {
    private static final String DATA = "{\"records\":["
            + "{\"SiteName\":\"松山\",\"County\":\"臺北市\",\"AQI\":\"55\",\"Status\":\"普通\",\"publishtime\":\"2026/11/21 21:00\"},"
            + "{\"sitename\":\"松山\",\"county\":\"臺北市\",\"aqi\":\"46\",\"status\":\"良好\",\"publishtime\":\"2026-11-21 22:00:00\"},"
            + "{\"sitename\":\"士林\",\"county\":\"臺北市\",\"aqi\":\"\",\"publishtime\":\"2026/11/21 22:00\"}]}";

    @Test public void recognizesStationPrefixesAndNaturalQuestions() {
        assertEquals("松山", AirQualityLookup.place("請查空氣品質 松山"));
        assertEquals("台北市松山", AirQualityLookup.place("臺北市松山的空氣品質如何？"));
        assertEquals("台北市松山", AirQualityLookup.place("請問臺北市松山的空氣品質如何？"));
        assertEquals("", AirQualityLookup.place("AQI"));
        assertNull(AirQualityLookup.place("查維基百科"));
    }

    @Test public void usesNewestStationDataAndReportsStaleness() throws JSONException {
        LookupAnswer answer = AirQualityLookup.answer(DATA, "台北市松山", taipei(21, 23));
        assertTrue(answer.text.contains("指標 46，良好"));
        assertTrue(answer.text.contains("2026-11-21 22:00:00"));
        assertFalse(answer.text.contains("超過三小時"));
        assertEquals(AirQualityLookup.SOURCE, answer.source);
        assertTrue(AirQualityLookup.answer(DATA, "松山", taipei(22, 4)).text.contains("超過三小時"));
    }

    @Test public void asksForStationAndHandlesMissingAqi() throws JSONException {
        assertTrue(AirQualityLookup.answer(DATA, "台北", taipei(21, 23)).text.contains("松山、士林"));
        assertTrue(AirQualityLookup.answer(DATA, "士林", taipei(21, 23)).text.contains("沒有可用"));
        assertTrue(AirQualityLookup.answer(DATA, "花蓮", taipei(21, 23)).text.contains("找不到"));
    }

    @Test public void comparesIsoTimestampsByTheirTimezoneOffset() throws JSONException {
        String data = "{\"records\":["
                + "{\"sitename\":\"松山\",\"county\":\"臺北市\",\"aqi\":\"40\","
                + "\"publishtime\":\"2026-11-21T22:00:00+08:00\"},"
                + "{\"sitename\":\"松山\",\"county\":\"臺北市\",\"aqi\":\"46\","
                + "\"publishtime\":\"2026-11-21T14:30:00Z\"},"
                + "{\"sitename\":\"松山\",\"county\":\"臺北市\",\"aqi\":\"44\","
                + "\"publishtime\":\"2026-11-21T22:15:30\"}]}";

        LookupAnswer answer = AirQualityLookup.answer(data, "松山", taipei(21, 23));

        assertTrue(answer.text.contains("指標 46"));
    }

    private static long taipei(int day, int hour) {
        Calendar time = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        time.clear();
        time.set(2026, Calendar.NOVEMBER, day, hour, 0);
        return time.getTimeInMillis();
    }
}
