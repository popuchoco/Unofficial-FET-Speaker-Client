package community.speaker.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AirQualityLookup {
    static final String SOURCE = "https://data.moenv.gov.tw/dataset/detail/AQX_P_432";
    private static final Pattern PREFIX = Pattern.compile("^(?:查詢|查一下|查)?(?:空氣品質|AQI)[:：]?(.*)$");
    private static final Pattern SUFFIX = Pattern.compile("^(.*?)(?:的)?(?:空氣品質|AQI)(?:怎麼樣|如何|是多少|多少)?$");
    private static final long RECENT_MILLIS = 3 * 60 * 60 * 1000L;

    private AirQualityLookup() {}

    static String place(String speech) {
        String query = SpeechPrefix.strip(normalize(speech)).replaceFirst("[?？。！!]+$", "");
        Matcher prefix = PREFIX.matcher(query);
        if (prefix.matches()) return prefix.group(1);
        Matcher suffix = SUFFIX.matcher(query);
        return suffix.matches() ? suffix.group(1).replaceFirst("^(查詢|查一下|查)", "") : null;
    }

    static LookupAnswer search(String place, String apiKey) throws IOException, JSONException {
        String url = "https://data.moenv.gov.tw/api/v2/aqx_p_432?format=json&limit=1000&api_key="
                + URLEncoder.encode(apiKey, "UTF-8");
        return answer(PublicDataClient.get(url, "application/json", 2 * 1024 * 1024, 5 * 60 * 1000L),
                place, System.currentTimeMillis());
    }

    static LookupAnswer answer(String json, String place, long now) throws JSONException {
        JSONArray records = new JSONObject(json).getJSONArray("records");
        String key = normalize(place).replaceFirst("測站$", "");
        List<JSONObject> matches = new ArrayList<>();
        Set<String> stations = new LinkedHashSet<>();
        for (int index = 0; index < records.length(); index++) {
            JSONObject row = records.getJSONObject(index);
            String name = field(row, "sitename", "SiteName");
            String county = field(row, "county", "County");
            if (name.isEmpty()) continue;
            String site = normalize(name);
            String area = normalize(county);
            if (key.equals(site) || key.equals(area + site)) {
                matches.add(row);
                stations.add(county + name);
            }
        }
        if (matches.isEmpty()) {
            StringJoiner choices = new StringJoiner("、");
            Set<String> names = new LinkedHashSet<>();
            for (int index = 0; index < records.length(); index++) {
                JSONObject row = records.getJSONObject(index);
                String county = field(row, "county", "County");
                String name = field(row, "sitename", "SiteName");
                String area = normalize(county);
                if (!name.isEmpty() && (key.equals(area) || key.equals(area.replaceFirst("[縣市]$", "")))) {
                    if (names.add(name)) choices.add(name);
                    if (names.size() == 5) break;
                }
            }
            return new LookupAnswer(names.isEmpty() ? "找不到這個空氣品質測站，請說出縣市或測站名稱。"
                    : "請指定測站，例如" + choices + "。", SOURCE);
        }
        if (stations.size() > 1) return new LookupAnswer("有多個同名測站，請加上縣市名稱。", SOURCE);

        JSONObject newest = matches.get(0);
        long published = -1;
        for (JSONObject row : matches) {
            long timestamp = publishedAt(field(row, "publishtime", "PublishTime"));
            if (timestamp > published) {
                newest = row;
                published = timestamp;
            }
        }
        String station = stations.iterator().next();
        String time = field(newest, "publishtime", "PublishTime");
        String aqi = field(newest, "aqi", "AQI");
        String status = field(newest, "status", "Status");
        if (published < 0 || !aqi.matches("[0-9]{1,3}")) {
            return new LookupAnswer(station + "測站目前沒有可用的 AQI 或發布時間。", SOURCE);
        }
        String freshness = now - published > RECENT_MILLIS ? "這筆資料已超過三小時。" : "";
        return new LookupAnswer(station + "測站，空氣品質指標 " + aqi
                + (status.isEmpty() ? "" : "，" + status) + "。發布時間 " + time + "。" + freshness, SOURCE);
    }

    private static String field(JSONObject row, String key, String alternate) {
        if (!row.isNull(key)) return row.optString(key).trim();
        return row.isNull(alternate) ? "" : row.optString(alternate).trim();
    }

    private static long publishedAt(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        format.setLenient(false);
        String normalized = value.replace('-', '/').replace('T', ' ');
        if (normalized.length() == 19 && normalized.endsWith(":00")) normalized = normalized.substring(0, 16);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(normalized, position);
        return date == null || position.getIndex() != normalized.length() ? -1 : date.getTime();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replace('臺', '台')
                .replaceAll("[\\s\\p{Z}]", "").toUpperCase(Locale.ROOT);
    }
}
