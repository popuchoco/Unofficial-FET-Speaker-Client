package community.speaker.client;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class WeatherLookupTest {
    private static final String SOURCE_DATA = "{\"records\":{\"Locations\":[{\"LocationsName\":\"臺北市\","
            + "\"Location\":[{\"LocationName\":\"信義區\",\"WeatherElement\":["
            + "{\"ElementName\":\"天氣預報綜合描述\",\"Time\":["
            + "{\"StartTime\":\"2026-11-21T18:00:00+08:00\",\"EndTime\":\"2026-11-22T06:00:00+08:00\","
            + "\"ElementValue\":[{\"WeatherDescription\":\"多雲。溫度18至20度。降雨機率10%。\"}]},"
            + "{\"StartTime\":\"2026-11-22T06:00:00+08:00\",\"EndTime\":\"2026-11-22T18:00:00+08:00\","
            + "\"ElementValue\":[{\"WeatherDescription\":\"晴時多雲。溫度20至25度。\"}]},"
            + "{\"StartTime\":\"2026-11-22T18:00:00+08:00\",\"EndTime\":\"2026-11-23T06:00:00+08:00\","
            + "\"ElementValue\":[{\"WeatherDescription\":\"多雲。溫度18至22度。降雨機率20%。\"}]}]}]}]}]}}";

    @Test public void parsesTaiwanDistrictAndRelativeDay() {
        WeatherLookup.Request request = WeatherLookup.parse("請查台北市信義區明天天氣。");
        assertEquals("臺北市信義區", request.place);
        assertEquals(1, request.days);
        assertEquals(-1, request.targetHour);
        assertEquals("F-D0047-063", WeatherLookup.dataset(request.place));
        assertEquals("F-D0047-071", WeatherLookup.dataset("新北市板橋區"));
        assertEquals("", WeatherLookup.parse("後天天氣").place);
        assertNull(WeatherLookup.dataset("信義區"));
        assertNull(WeatherLookup.parse("台股2330"));

        WeatherLookup.Request tonight = WeatherLookup.parse("請查台北市信義區今晚天氣");
        assertEquals("臺北市信義區", tonight.place);
        assertEquals(0, tonight.days);
        assertEquals(21, tonight.targetHour);

        WeatherLookup.Request tomorrowNight = WeatherLookup.parse("查詢臺北市信義區明天晚上天氣");
        assertEquals("臺北市信義區", tomorrowNight.place);
        assertEquals(1, tomorrowNight.days);
        assertEquals(21, tomorrowNight.targetHour);

        WeatherLookup.Request dayAfterMorning = WeatherLookup.parse("後天早上臺北市信義區的天氣如何");
        assertEquals("臺北市信義區", dayAfterMorning.place);
        assertEquals(2, dayAfterMorning.days);
        assertEquals(9, dayAfterMorning.targetHour);
    }

    @Test public void selectsRelevantForecastAndRefusesUnavailablePeriods() throws Exception {
        assertTrue(WeatherLookup.answer(SOURCE_DATA, "台北市信義區", 0, tonight()).text
                .contains("降雨機率10%"));
        assertTrue(WeatherLookup.answer(SOURCE_DATA, "臺北市信義區", 1, tonight()).text
                .contains("11月22日 6點到11月22日 18點"));
        assertTrue(WeatherLookup.answer(SOURCE_DATA, "臺北市信義區", 1, 21, tonight()).text
                .contains("11月22日 18點到11月23日 6點"));
        assertTrue(WeatherLookup.answer(SOURCE_DATA, "臺北市信義區", 2, tonight()).text
                .contains("沒有涵蓋"));
        assertTrue(WeatherLookup.answer(SOURCE_DATA, "臺北市士林區", 0, tonight()).text
                .contains("找不到"));
    }

    private static long tonight() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 21, 21, 0);
        return now.getTimeInMillis();
    }
}
