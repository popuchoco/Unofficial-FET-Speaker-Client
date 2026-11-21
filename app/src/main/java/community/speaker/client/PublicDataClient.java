package community.speaker.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class PublicDataClient {
    private static final MemoryResponseCache CACHE = new MemoryResponseCache(6 * 1024 * 1024L, 8);
    private static final String USER_AGENT =
            "UnofficialFETSpeakerClient/0.1 (https://github.com/popuchoco/Unofficial-FET-Speaker-Client)";

    private PublicDataClient() {}

    static String get(String url, String mediaType, int sizeLimit) throws IOException {
        return get(url, mediaType, sizeLimit, 0);
    }

    static String get(String url, String mediaType, int sizeLimit, long cacheTtlMillis) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Lookup cancelled");
        String cacheKey = null;
        if (cacheTtlMillis > 0) {
            cacheKey = cacheKey(url, mediaType);
            String cached = CACHE.get(cacheKey, elapsedMillis());
            if (cached != null) return cached;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", mediaType);
        connection.setRequestProperty("Accept-Language", "zh-TW");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP error");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int length;
                while ((length = input.read(chunk)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Lookup cancelled");
                    if (data.size() + length > sizeLimit) throw new IOException("Response exceeds size limit");
                    data.write(chunk, 0, length);
                }
                String response = new String(data.toByteArray(), StandardCharsets.UTF_8);
                if (cacheTtlMillis > 0) CACHE.put(cacheKey, response, elapsedMillis(), cacheTtlMillis);
                return response;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static long elapsedMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static String cacheKey(String url, String mediaType) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((mediaType + "\n" + url).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(digest.length * 2);
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return key.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("Response cache unavailable", impossible);
        }
    }
}
