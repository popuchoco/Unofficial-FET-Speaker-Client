package community.speaker.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExchangeLookup {
    static final String SOURCE = "https://rate.landbank.com.tw/zh-TW/Foreign/";
    private static final Map<String, String> CURRENCIES = new HashMap<>();
    private static final Pattern CONVERSION = Pattern.compile(
            "^(?:請幫我|幫我|請)?(現金|即期)?([0-9零〇一二兩三四五六七八九十百千萬點.]+)"
                    + "([\\p{L}]+?)(?:可以)?(?:換成|換算成|換|等於|是)(?:多少)?"
                    + "([\\p{L}]+?)(?:是多少|多少)?(現金|即期)?$");

    static {
        currency("TWD", "臺幣", "臺灣幣", "新臺幣", "新臺幣元", "NTD");
        currency("USD", "美元", "美金");
        currency("JPY", "日圓", "日元", "日幣");
        currency("GBP", "英鎊");
        currency("HKD", "港幣", "港元");
        currency("AUD", "澳幣", "澳元");
        currency("CAD", "加拿大幣", "加幣", "加元");
        currency("SGD", "新加坡幣", "新幣");
        currency("CHF", "瑞士法郎");
        currency("SEK", "瑞典幣", "瑞典克朗");
        currency("ZAR", "南非幣", "南非蘭特");
        currency("THB", "泰幣", "泰銖");
        currency("NZD", "紐西蘭幣", "紐幣");
        currency("EUR", "歐元");
        currency("CNY", "人民幣");
    }

    private ExchangeLookup() {}

    static final class Request {
        final BigDecimal amount;
        final String from;
        final String to;
        final boolean cash;

        Request(BigDecimal amount, String from, String to, boolean cash) {
            this.amount = amount;
            this.from = from;
            this.to = to;
            this.cash = cash;
        }
    }

    static Request parse(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT)
                .replace('台', '臺').replaceAll("[\\s\\p{Z}]", "").replaceFirst("[。?!！？]+$", "");
        Matcher match = CONVERSION.matcher(normalized);
        if (!match.matches()) return null;
        String from = CURRENCIES.get(match.group(3));
        String to = CURRENCIES.get(match.group(4));
        if (from == null || to == null || from.equals(to)) return null;
        String amount = LocalAnswer.replaceChineseNumbers(match.group(2));
        if (!amount.matches("[0-9]{1,15}(?:\\.[0-9]{1,8})?")) return null;
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0) return null;
        return new Request(value, from, to, "現金".equals(match.group(1)) || "現金".equals(match.group(5)));
    }

    static LookupAnswer search(Request request) throws IOException {
        if (!"TWD".equals(request.from) && !"TWD".equals(request.to)) return crossCurrency();
        String csv = PublicDataClient.get(SOURCE + "Export?type=csv", "text/plain,text/csv", 64 * 1024);
        return answer(csv, request, System.currentTimeMillis());
    }

    static LookupAnswer answer(String csv, Request request, long fetchedAt) throws IOException {
        if (!"TWD".equals(request.from) && !"TWD".equals(request.to)) return crossCurrency();
        boolean bankBuysForeign = "TWD".equals(request.to);
        String currency = bankBuysForeign ? request.from : request.to;
        int quoteColumn = (request.cash ? 3 : 1) + (bankBuysForeign ? 0 : 1);
        boolean validFeed = false;
        for (String line : csv.replaceFirst("^\uFEFF", "").split("\\r?\\n")) {
            String[] fields = line.trim().split(",", -1);
            if (fields.length != 5 || !fields[0].matches("[A-Z]{3}")) continue;
            validFeed = true;
            if (!fields[0].equals(currency)) continue;
            BigDecimal quote;
            try {
                quote = new BigDecimal(fields[quoteColumn].trim());
            } catch (NumberFormatException invalid) {
                break;
            }
            if (quote.signum() <= 0) break;
            BigDecimal converted = bankBuysForeign ? request.amount.multiply(quote)
                    : request.amount.divide(quote, 2, RoundingMode.HALF_UP);
            SimpleDateFormat time = new SimpleDateFormat("M月d日 H點mm分", Locale.TAIWAN);
            time.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
            String method = (request.cash ? "現金" : "即期") + (bankBuysForeign ? "買入" : "賣出");
            return new LookupAnswer("資料來源為臺灣土地銀行公開牌告，" + method + "匯率 " + number(quote) + "，"
                    + number(request.amount) + " " + request.from + "約等於"
                    + number(converted.setScale(2, RoundingMode.HALF_UP)) + " " + request.to
                    + "。結果僅供參考，實際成交以銀行當時牌告為準。查詢時間 "
                    + time.format(new Date(fetchedAt)) + "。", SOURCE);
        }
        if (!validFeed) throw new IOException("Exchange feed has no quote rows");
        return new LookupAnswer("臺灣土地銀行目前沒有這個幣別可用的"
                + (request.cash ? "現金" : "即期") + "牌告匯率。", SOURCE);
    }

    private static LookupAnswer crossCurrency() {
        return new LookupAnswer("目前可換算新臺幣和外幣。請在其中一邊指定新臺幣。", null);
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void currency(String code, String... aliases) {
        CURRENCIES.put(code, code);
        for (String alias : aliases) CURRENCIES.put(alias, code);
    }
}
