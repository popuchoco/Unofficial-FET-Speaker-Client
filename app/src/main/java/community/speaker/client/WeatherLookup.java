package community.speaker.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WeatherLookup {
    static final String SOURCE = "https://opendata.cwa.gov.tw/dist/opendata-swagger.html";
    private static final TimeZone TAIPEI = TimeZone.getTimeZone("Asia/Taipei");
    private static final String[] COUNTIES = {
            "宜蘭縣", "桃園市", "新竹縣", "苗栗縣", "彰化縣", "南投縣", "雲林縣", "嘉義縣",
            "屏東縣", "臺東縣", "花蓮縣", "澎湖縣", "基隆市", "新竹市", "嘉義市", "臺北市",
            "高雄市", "新北市", "臺中市", "臺南市", "連江縣", "金門縣"
    };
    private static final Pattern PREFIX = Pattern.compile("^(?:查詢|查一下|查)?(?:天氣預報|天氣)[:：]?(.*)$");
    private static final Pattern SUFFIX = Pattern.compile("^(.*?)(?:的)?(?:天氣預報|天氣)(?:怎麼樣|如何)?$");

    private WeatherLookup() {}

    static final class Request {
        final String place;
        final int days;

        Request(String place, int days) {
            this.place = place;
            this.days = days;
        }
    }

    static Request parse(String speech) {
        String normalized = normalize(speech).replaceFirst("[?？。！!]+$", "").replaceFirst("^(請幫我|幫我|請)", "");
        Matcher prefix = PREFIX.matcher(normalized);
        Matcher suffix = SUFFIX.matcher(normalized);
        String query;
        if (prefix.matches()) query = prefix.group(1);
        else if (suffix.matches()) query = suffix.group(1).replaceFirst("^(查詢|查一下|查)", "");
        else return null;
        int days = query.contains("後天") ? 2 : query.contains("明天") ? 1 : 0;
        return new Request(query.replaceFirst("今天|明天|後天", ""), days);
    }

    static String dataset(String place) {
        String normalized = normalize(place);
        for (int index = 0; index < COUNTIES.length; index++) {
            String county = COUNTIES[index];
            if (normalized.startsWith(county) && normalized.length() > county.length()) {
                return String.format(Locale.ROOT, "F-D0047-%03d", 3 + index * 4);
            }
        }
        return null;
    }

    static LookupAnswer search(String place, int days, String key) throws IOException, JSONException {
        String dataset = dataset(place);
        if (dataset == null) throw new IllegalArgumentException("County and district are required");
        String url = "https://opendata.cwa.gov.tw/api/v1/rest/datastore/" + dataset
                + "?Authorization=" + URLEncoder.encode(key, "UTF-8") + "&format=JSON";
        return answer(PublicDataClient.get(url, "application/json", 4 * 1024 * 1024, 15 * 60 * 1000L),
                place, days, System.currentTimeMillis());
    }

    static LookupAnswer answer(String json, String place, int days, long now) throws JSONException {
        JSONArray groups = new JSONObject(json).getJSONObject("records").getJSONArray("Locations");
        String key = normalize(place);
        for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
            JSONObject group = groups.getJSONObject(groupIndex);
            String county = group.getString("LocationsName");
            JSONArray locations = group.getJSONArray("Location");
            for (int index = 0; index < locations.length(); index++) {
                JSONObject location = locations.getJSONObject(index);
                String name = county + location.getString("LocationName");
                if (normalize(name).equals(key)) {
                    return forecast(location.getJSONArray("WeatherElement"), name, days, now);
                }
            }
        }
        return new LookupAnswer("找不到這個鄉鎮市區，請確認縣市和區名。", SOURCE);
    }

    private static LookupAnswer forecast(JSONArray elements, String name, int days, long now) throws JSONException {
        Calendar target = Calendar.getInstance(TAIPEI);
        target.setTimeInMillis(now);
        if (days > 0) {
            target.add(Calendar.DATE, days);
            target.set(Calendar.HOUR_OF_DAY, 9);
            target.set(Calendar.MINUTE, 0);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
        }
        long requested = target.getTimeInMillis();
        for (int index = 0; index < elements.length(); index++) {
            JSONObject factor = elements.getJSONObject(index);
            JSONArray times = factor.getJSONArray("Time");
            for (int i = 0; i < times.length(); i++) {
                JSONObject slot = times.getJSONObject(i);
                long start = instant(slot.optString("StartTime"));
                long end = instant(slot.optString("EndTime"));
                if (start < 0 || end < 0 || requested < start || requested >= end) continue;
                JSONArray values = slot.optJSONArray("ElementValue");
                if (values == null || values.length() == 0) continue;
                JSONObject value = values.getJSONObject(0);
                String summary = value.isNull("WeatherDescription") ? "" : value.optString("WeatherDescription").trim();
                if (summary.isEmpty() || summary.equals("-")) continue;
                SimpleDateFormat clock = new SimpleDateFormat("M月d日 H點", Locale.TAIWAN);
                clock.setTimeZone(TAIPEI);
                return new LookupAnswer(name + "，" + clock.format(new Date(start)) + "到"
                        + clock.format(new Date(end)) + "預報，" + summary, SOURCE);
            }
        }
        return new LookupAnswer(name + "目前沒有涵蓋指定時段的預報。", SOURCE);
    }

    private static long instant(String input) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(input, position);
        return date == null || position.getIndex() != input.length() ? -1 : date.getTime();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replace('台', '臺').replaceAll("[\\s\\p{Z}]", "");
    }
}
