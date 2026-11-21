package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MemoryResponseCacheTest {
    @Test
    public void cachedResponsesExpireAtTheConfiguredTtl() {
        MemoryResponseCache cache = new MemoryResponseCache(32, 4);
        cache.put("weather", "forecast", 1000, 200);

        assertEquals("forecast", cache.get("weather", 1199));
        assertNull(cache.get("weather", 1200));
    }

    @Test
    public void leastRecentlyUsedEntryIsEvictedWhenByteLimitIsReached() {
        MemoryResponseCache cache = new MemoryResponseCache(8, 3);
        cache.put("first", "1234", 0, 1000);
        cache.put("second", "5678", 0, 1000);
        cache.get("first", 1);
        cache.put("third", "abcd", 2, 1000);

        assertEquals("1234", cache.get("first", 3));
        assertNull(cache.get("second", 3));
        assertEquals("abcd", cache.get("third", 3));
    }

    @Test
    public void oversizedResponsesAreNotCached() {
        MemoryResponseCache cache = new MemoryResponseCache(3, 2);
        cache.put("large", "four", 0, 1000);

        assertNull(cache.get("large", 1));
    }
}
