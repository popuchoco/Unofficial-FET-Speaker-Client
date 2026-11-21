package community.speaker.client;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalAnswerTest {
    private final Calendar time = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));

    public LocalAnswerTest() {
        time.clear();
        time.set(2026, Calendar.NOVEMBER, 21, 19, 5);
    }

    @Test public void respondsWithLocalTimeAndDate() {
        assertEquals("現在是19點05分。", answer("請問現在幾點？"));
        assertTrue(answer("今天星期幾").contains("2026年11月21日"));
        assertTrue(answer("二〇二四年二月二十九日是星期幾").contains("2024年2月29日"));
        assertEquals("2024年2月有29天。", answer("查萬年曆2024年2月有幾天"));
        assertEquals("2026年11月第3個星期五是20日。",
                answer("查萬年曆2026年11月第三個星期五"));
        assertEquals("這個日期不存在，請再確認年月日。", answer("2025年2月29日星期幾"));
    }

    @Test public void calculatesDecimalAndChineseExpressions() {
        assertEquals("答案是14。", answer("二加三乘以四是多少"));
        assertEquals("答案是20。", answer("（2＋3）乘以4"));
        assertEquals("答案是-1.25。", answer("負二點五除以二"));
        assertEquals("除數不能是零。", answer("8除以0"));
        assertEquals("算式不完整，請再說一次。", answer("8加"));
    }

    @Test public void convertsTaiwaneseUnitsAndTemperatures() {
        assertEquals("2台斤等於1.2公斤。", answer("兩台斤換成公斤"));
        assertEquals("2台兩等於75公克。", answer("兩台兩等於多少公克"));
        assertEquals("2千米等於2000公尺。", answer("兩千米等於多少公尺"));
        assertEquals("-40攝氏度等於-40華氏度。", answer("攝氏負四十度換成華氏度"));
        assertEquals("這兩個單位無法換算，請確認單位。", answer("12公里換成公升"));
        assertEquals("請說明要使用台斤或台兩。", answer("一斤換成公克"));
    }

    @Test public void convertsTimeAndSpeedUnits() {
        assertEquals("90分鐘等於1.5小時。", answer("90分鐘換成小時"));
        assertEquals("1天等於24小時。", answer("一天換成小時"));
        assertEquals("1週等於7天。", answer("一週換成天"));
        assertEquals("72公里每小時等於20公尺每秒。", answer("72公里每小時換成公尺每秒"));
        assertEquals("5公尺每秒等於18公里每小時。", answer("5公尺每秒換成公里每小時"));
        assertEquals("這兩個單位無法換算，請確認單位。", answer("3小時換成公尺"));
    }

    @Test public void leavesOtherQuestionsForTheirOwnHandlers() {
        assertNull(answer("台北明天天氣如何"));
        assertNull(answer("一百美元換成台幣"));
    }

    private String answer(String question) { return LocalAnswer.reply(question, time); }
}
