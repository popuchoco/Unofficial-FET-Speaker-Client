package community.speaker.client;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalAnswer {
    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final Locale TAIWAN = Locale.TAIWAN;
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日號]");
    private static final Pattern CONVERSION = Pattern.compile(
            "([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))([^0-9]+?)(?:等於|是多少|換算成|換成|是|有)(?:多少)?([^0-9]+)");
    private static final Pattern CHINESE_NUMBER = Pattern.compile("(?:[零〇一二三四五六七八九十百萬點]|(?<!台)兩|千(?!克|米))+");
    private static final Map<String, Unit> UNITS = new HashMap<>();

    static {
        units("長度", "0.001", "毫米", "公釐");
        units("長度", "0.01", "公分", "厘米");
        units("長度", "1", "公尺", "米");
        units("長度", "1000", "公里", "千米");
        units("重量", "1", "公克", "克");
        units("重量", "1000", "公斤", "千克");
        units("重量", "600", "台斤");
        units("重量", "37.5", "台兩");
        units("容量", "1", "毫升", "西西");
        units("容量", "1000", "公升", "升");
        units("面積", "1", "平方公尺", "平方米");
        units("面積", "3.305785123966942", "坪");
    }

    private LocalAnswer() {}

    static String reply(String input, Calendar now) {
        String query = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]", "")
                .replaceAll("[?？!！。]+$", "")
                .replaceFirst("^(請問|請幫我|幫我)", "");
        if (query.matches("(現在)?(是)?幾點(鐘|了)?|現在時間")) {
            return "現在是" + formatted(now, "HH點mm分") + "。";
        }
        if (query.matches("今天(是)?(日期|幾月幾日|幾月幾號|幾號|星期幾|禮拜幾)")) {
            return formatted(now, "今天是yyyy年M月d日EEEE") + "。";
        }
        String numeric = replaceChineseNumbers(query);
        Matcher date = DATE.matcher(numeric);
        if (date.find() && (numeric.contains("星期") || numeric.contains("禮拜"))) {
            Calendar day = (Calendar) now.clone();
            day.setLenient(false);
            try {
                int year = date.group(1) == null ? day.get(Calendar.YEAR) : Integer.parseInt(date.group(1));
                day.set(year, Integer.parseInt(date.group(2)) - 1, Integer.parseInt(date.group(3)));
                return formatted(day, "yyyy年M月d日是EEEE") + "。";
            } catch (IllegalArgumentException invalidDate) {
                return "這個日期不存在，請再確認年月日。";
            }
        }
        if (query.matches("今天(是)?農曆(幾號|幾月幾日|幾月幾號|多少)?|農曆日期")) {
            android.icu.util.ChineseCalendar lunar = new android.icu.util.ChineseCalendar(
                    android.icu.util.TimeZone.getTimeZone(now.getTimeZone().getID()));
            lunar.setTimeInMillis(now.getTimeInMillis());
            return "今天是農曆" + (lunar.get(android.icu.util.Calendar.IS_LEAP_MONTH) == 1 ? "閏" : "")
                    + (lunar.get(android.icu.util.Calendar.MONTH) + 1) + "月"
                    + lunar.get(android.icu.util.Calendar.DAY_OF_MONTH) + "日。";
        }
        String converted = convert(numeric);
        if (converted != null) return converted;

        String expression = numeric.replaceFirst("^(計算|算一下|算)", "")
                .replaceFirst("(等於多少|是多少|等於幾|等於|=)$", "")
                .replace("乘以", "*").replace("乘", "*").replace("×", "*")
                .replace("除以", "/").replace("÷", "/")
                .replace("加上", "+").replace("加", "+").replace("減去", "-").replace("減", "-")
                .replace("負", "-");
        if (expression.length() > 120 || !expression.matches("[0-9.+*/()-]+")
                || !expression.matches(".*[+*/-].*")) return null;
        try {
            return "答案是" + display(new Expression(expression).evaluate()) + "。";
        } catch (ArithmeticException error) {
            return "除數不能是零。";
        } catch (IllegalArgumentException error) {
            return "算式不完整，請再說一次。";
        }
    }

    private static String convert(String text) {
        String normalized = text.replaceAll("^(攝氏|華氏)([負+-]?[0-9.]+)度", "$2$1度").replace("負", "-");
        Matcher matcher = CONVERSION.matcher(normalized);
        if (!matcher.matches()) return null;
        String source = matcher.group(2);
        String target = matcher.group(3);
        boolean sourceCelsius = isTemperature(source, "攝氏", "C");
        boolean targetCelsius = isTemperature(target, "攝氏", "C");
        boolean sourceFahrenheit = isTemperature(source, "華氏", "F");
        boolean targetFahrenheit = isTemperature(target, "華氏", "F");
        Unit from = UNITS.get(source);
        Unit to = UNITS.get(target);
        if (source.equals("斤") || target.equals("斤") || source.equals("兩") || target.equals("兩")) {
            return "請說明要使用台斤或台兩。";
        }
        if (from == null && to == null && !sourceCelsius && !targetCelsius && !sourceFahrenheit && !targetFahrenheit) {
            return null;
        }
        BigDecimal amount = new BigDecimal(matcher.group(1));
        BigDecimal result;
        if ((sourceCelsius || sourceFahrenheit) && (targetCelsius || targetFahrenheit)) {
            result = amount;
            if (sourceCelsius && targetFahrenheit) {
                result = amount.multiply(new BigDecimal("1.8")).add(new BigDecimal("32"));
            } else if (sourceFahrenheit && targetCelsius) {
                result = amount.subtract(new BigDecimal("32")).divide(new BigDecimal("1.8"), PRECISION);
            }
        } else if (from != null && to != null && from.kind.equals(to.kind)) {
            result = amount.multiply(from.factor).divide(to.factor, PRECISION);
        } else {
            return "這兩個單位無法換算，請確認單位。";
        }
        return display(amount) + source + "等於" + display(result) + target + "。";
    }

    private static boolean isTemperature(String text, String unit, String symbol) {
        return text.equals(unit) || text.equals(unit + "度") || text.equals("度" + unit)
                || text.equalsIgnoreCase("度" + symbol);
    }

    private static void units(String kind, String factor, String... names) {
        Unit unit = new Unit(kind, new BigDecimal(factor));
        for (String name : names) UNITS.put(name, unit);
    }

    private static final class Unit {
        final String kind;
        final BigDecimal factor;
        Unit(String kind, BigDecimal factor) {
            this.kind = kind;
            this.factor = factor;
        }
    }

    private static String formatted(Calendar calendar, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, TAIWAN);
        format.setTimeZone(calendar.getTimeZone());
        return format.format(calendar.getTime());
    }

    private static String display(BigDecimal number) {
        return number.round(new MathContext(10)).stripTrailingZeros().toPlainString();
    }

    static String replaceChineseNumbers(String input) {
        Matcher matcher = CHINESE_NUMBER.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = chineseNumber(matcher.group());
            matcher.appendReplacement(result, value == null ? matcher.group() : value);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String chineseNumber(String text) {
        String digits = "零一二三四五六七八九";
        StringBuilder sequence = new StringBuilder();
        long total = 0;
        long section = 0;
        boolean hasUnits = false;
        boolean decimal = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            int digit = ch == '兩' ? 2 : ch == '〇' ? 0 : digits.indexOf(ch);
            if (digit >= 0) {
                sequence.append(digit);
                continue;
            }
            if (sequence.length() > 8 || decimal) return null;
            long number = sequence.length() == 0 ? 0 : Long.parseLong(sequence.toString());
            if (ch == '點') {
                if (index == text.length() - 1 || index == 0) return null;
                section += number;
                sequence.setLength(0);
                decimal = true;
                continue;
            }
            hasUnits = true;
            if (ch == '萬') {
                total += (section + number) * 10_000;
                section = 0;
            } else {
                int factor = ch == '千' ? 1000 : ch == '百' ? 100 : 10;
                section += (sequence.length() == 0 ? 1 : number) * factor;
            }
            sequence.setLength(0);
        }
        if (decimal) return (total + section) + "." + sequence;
        if (!hasUnits) return sequence.toString();
        if (sequence.length() > 8) return null;
        return Long.toString(total + section + (sequence.length() == 0 ? 0 : Long.parseLong(sequence.toString())));
    }

    private static final class Expression {
        private final String text;
        private int position;

        Expression(String text) { this.text = text; }

        BigDecimal evaluate() {
            BigDecimal result = sum();
            if (position != text.length()) throw new IllegalArgumentException();
            return result;
        }

        private BigDecimal sum() {
            BigDecimal value = product();
            while (true) {
                if (eat('+')) value = value.add(product(), PRECISION);
                else if (eat('-')) value = value.subtract(product(), PRECISION);
                else return value;
            }
        }

        private BigDecimal product() {
            BigDecimal value = factor();
            while (true) {
                if (eat('*')) value = value.multiply(factor(), PRECISION);
                else if (eat('/')) value = value.divide(factor(), PRECISION);
                else return value;
            }
        }

        private BigDecimal factor() {
            if (eat('+')) return factor();
            if (eat('-')) return factor().negate();
            if (eat('(')) {
                BigDecimal result = sum();
                if (!eat(')')) throw new IllegalArgumentException();
                return result;
            }
            int start = position;
            while (position < text.length() && "0123456789.".indexOf(text.charAt(position)) >= 0) position++;
            return new BigDecimal(text.substring(start, position));
        }

        private boolean eat(char expected) {
            if (position >= text.length() || text.charAt(position) != expected) return false;
            position++;
            return true;
        }
    }
}
