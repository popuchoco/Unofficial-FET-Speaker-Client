package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikipediaLookupTest {
    @Test public void matchesOnlyExplicitEncyclopediaQueries() {
        assertEquals("台北101", WikipediaLookup.topic("請幫我查維基百科 台北101。"));
        assertEquals("中央山脈", WikipediaLookup.topic("維基百科搜尋：中央山脈"));
        assertEquals("", WikipediaLookup.topic("查維基百科"));
        assertNull(WikipediaLookup.topic("台北天氣"));
        assertNull(WikipediaLookup.topic("三分鐘後提醒我查維基百科"));
    }
}
