package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StockLookupTest {
    @Test public void recognizesQuotesWithoutChangingOtherCommands() {
        assertEquals("2330", StockLookup.symbol("請幫我查台股 ２３３０。"));
        assertEquals("台積電", StockLookup.symbol("台積電的收盤價是多少？"));
        assertEquals("台積電", StockLookup.symbol("查詢股價：台積電"));
        assertEquals("", StockLookup.symbol("查台股"));
        assertNull(StockLookup.symbol("台積電是什麼公司"));
    }

    @Test public void interpretsThePublishedTradingDate() {
        assertEquals("2026年11月20日", StockLookup.tradingDate("1151120"));
        assertEquals("2024年2月29日", StockLookup.tradingDate("1130229"));
        assertNull(StockLookup.tradingDate("1140229"));
        assertNull(StockLookup.tradingDate("20261120"));
    }
}
