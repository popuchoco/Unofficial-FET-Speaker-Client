package community.speaker.client;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class MemoryResponseCache {
    private final long maxBytes;
    private final int maxEntries;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long byteCount;

    MemoryResponseCache(long maxBytes, int maxEntries) {
        if (maxBytes <= 0 || maxEntries <= 0) throw new IllegalArgumentException("Cache limits must be positive");
        this.maxBytes = maxBytes;
        this.maxEntries = maxEntries;
    }

    synchronized String get(String key, long nowMillis) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (nowMillis - entry.storedAtMillis >= entry.ttlMillis) {
            remove(key);
            return null;
        }
        return entry.value;
    }

    synchronized void put(String key, String value, long nowMillis, long ttlMillis) {
        if (ttlMillis <= 0) return;
        long size = value.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxBytes) return;
        remove(key);
        removeExpired(nowMillis);
        entries.put(key, new Entry(value, nowMillis, ttlMillis, size));
        byteCount += size;
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while ((byteCount > maxBytes || entries.size() > maxEntries) && iterator.hasNext()) {
            Map.Entry<String, Entry> oldest = iterator.next();
            byteCount -= oldest.getValue().sizeBytes;
            iterator.remove();
        }
    }

    private void removeExpired(long nowMillis) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (nowMillis - entry.storedAtMillis >= entry.ttlMillis) {
                byteCount -= entry.sizeBytes;
                iterator.remove();
            }
        }
    }

    private void remove(String key) {
        Entry removed = entries.remove(key);
        if (removed != null) byteCount -= removed.sizeBytes;
    }

    private static final class Entry {
        final String value;
        final long storedAtMillis;
        final long ttlMillis;
        final long sizeBytes;

        Entry(String value, long storedAtMillis, long ttlMillis, long sizeBytes) {
            this.value = value;
            this.storedAtMillis = storedAtMillis;
            this.ttlMillis = ttlMillis;
            this.sizeBytes = sizeBytes;
        }
    }
}
