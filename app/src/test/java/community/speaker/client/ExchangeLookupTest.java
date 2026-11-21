package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExchangeLookupTest {
    private static final String RATES = "\uFEFFUSD,30,32,29,33\r\nJPY,0.2,0.21,0,0";

    @Test public void usesBankBuyQuoteWhenExchangingForeignCurrencyIntoTaiwanDollars() throws Exception {
        ExchangeLookup.Request request = ExchangeLookup.parse("請幫我一百美元換成多少台幣？");
        LookupAnswer answer = ExchangeLookup.answer(RATES, request, 0);
        assertTrue(answer.text.contains("即期買入匯率 30"));
        assertTrue(answer.text.contains("100 USD約等於3000 TWD"));
        assertTrue(answer.text.contains("查詢時間"));
        assertEquals(ExchangeLookup.SOURCE, answer.source);
    }

    @Test public void usesBankSellQuoteForTaiwanDollarToForeignCash() throws Exception {
        LookupAnswer answer = ExchangeLookup.answer(RATES, ExchangeLookup.parse("現金三百三十臺幣換美金"), 0);
        assertTrue(answer.text.contains("現金賣出匯率 33"));
        assertTrue(answer.text.contains("330 TWD約等於10 USD"));
    }

    @Test public void handlesMissingRatesAndRejectsInvalidFeeds() throws Exception {
        assertTrue(ExchangeLookup.answer(RATES, ExchangeLookup.parse("一百日圓換臺幣現金"), 0)
                .text.contains("沒有"));
        assertNull(ExchangeLookup.parse("兩台斤換成公斤"));
        assertNull(ExchangeLookup.parse("0美金換台幣"));
        assertNull(ExchangeLookup.search(ExchangeLookup.parse("1美元換日圓")).source);
        assertThrows(java.io.IOException.class, () ->
                ExchangeLookup.answer("<html></html>", ExchangeLookup.parse("1美元換臺幣"), 0));
    }
}
