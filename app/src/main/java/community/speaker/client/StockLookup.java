package community.speaker.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StockLookup {
    private static final Pattern PREFIX = Pattern.compile("^(?:查詢|查一下|查)?(?:台股|臺股|股價|股票|收盤價)[:：]?(.*)$");
    private static final Pattern SUFFIX = Pattern.compile("^(.*?)(?:的)?(?:收盤價|股價)(?:是多少|多少)?$");
    private static final String LISTED_SOURCE = "https://openapi.twse.com.tw/";
    private static final String OTC_SOURCE = "https://www.tpex.org.tw/openapi/";

    private StockLookup() {}

    static String symbol(String speech) {
        String query = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]", "").replaceFirst("[?？。！!]+$", "")
                .replaceFirst("^(請幫我|幫我|請)", "");
        Matcher prefix = PREFIX.matcher(query);
        String term;
        if (prefix.matches()) {
            term = prefix.group(1).replaceFirst("(的)?(收盤價|股價)(是多少|多少)?$", "");
        } else {
            Matcher suffix = SUFFIX.matcher(query);
            if (!suffix.matches()) return null;
            term = suffix.group(1).replaceFirst("^(查詢|查一下|查)", "");
        }
        return term;
    }

    static LookupAnswer search(String symbol) throws IOException {
        String key = symbol.toUpperCase(Locale.ROOT);
        IOException unavailable = null;
        boolean loaded = false;
        List<String> suggestions = new ArrayList<>();
        for (boolean listed : new boolean[] { true, false }) {
            try {
                LookupAnswer answer = find(key, listed, suggestions);
                if (answer != null) return answer;
                loaded = true;
            } catch (IOException | JSONException error) {
                unavailable = new IOException("Market data unavailable", error);
            }
        }
        if (!loaded && unavailable != null) throw unavailable;
        if (!suggestions.isEmpty()) {
            return new LookupAnswer("請用股票代號或完整名稱再查一次。可能符合 " + android.text.TextUtils.join("、", suggestions) + "。", null);
        }
        return new LookupAnswer(unavailable == null ? "找不到這檔股票的最近收盤資料，請確認代號。"
                : "部分市場資料暫時無法取得，請確認代號或稍後再試。", null);
    }

    private static LookupAnswer find(String key, boolean listed, List<String> suggestions) throws IOException, JSONException {
        String endpoint = listed ? LISTED_SOURCE + "v1/exchangeReport/STOCK_DAY_ALL"
                : OTC_SOURCE + "v1/tpex_mainboard_quotes";
        JSONArray rows = new JSONArray(PublicDataClient.get(endpoint, "application/json", 2 * 1024 * 1024, 2 * 60 * 1000L));
        if (rows.length() == 0) throw new IOException("Empty market dataset");
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String code = row.optString(listed ? "Code" : "SecuritiesCompanyCode");
            String name = row.optString(listed ? "Name" : "CompanyName");
            if (code.equalsIgnoreCase(key) || name.equalsIgnoreCase(key)) {
                String source = listed ? LISTED_SOURCE : OTC_SOURCE;
                String date = tradingDate(row.optString("Date"));
                String close = row.optString(listed ? "ClosingPrice" : "Close");
                if (date == null || !close.matches("[0-9]+(?:\\.[0-9]+)?")) {
                    return new LookupAnswer(name + "，這個市場目前未提供可用的收盤價。", source);
                }
                String change = row.optString("Change");
                String movement = change.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")
                        ? movement(change) : "";
                return new LookupAnswer(date + "，" + (listed ? "上市" : "上櫃") + name + "，代號"
                        + code + "，收盤價" + close + "元" + movement + "。", source);
            }
            if (suggestions.size() < 3 && name.toUpperCase(Locale.ROOT).contains(key)) {
                suggestions.add(name + " " + code);
            }
        }
        return null;
    }

    private static String movement(String change) {
        java.math.BigDecimal value = new java.math.BigDecimal(change);
        if (value.signum() == 0) return "，持平";
        return (value.signum() < 0 ? "，下跌" : "，上漲") + value.abs().stripTrailingZeros().toPlainString() + "元";
    }

    static String tradingDate(String date) {
        if (!date.matches("[0-9]{7}")) return null;
        int year = Integer.parseInt(date.substring(0, 3)) + 1911;
        int month = Integer.parseInt(date.substring(3, 5));
        int day = Integer.parseInt(date.substring(5, 7));
        java.util.Calendar check = new java.util.GregorianCalendar();
        check.clear();
        check.setLenient(false);
        check.set(year, month - 1, day);
        try {
            check.getTimeInMillis();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return year + "年" + month + "月" + day + "日";
    }
}
