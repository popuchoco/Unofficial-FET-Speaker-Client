package community.speaker.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class PublicDataClient {
    private static final String USER_AGENT =
            "UnofficialFETSpeakerClient/0.1 (https://github.com/popuchoco/Unofficial-FET-Speaker-Client)";

    private PublicDataClient() {}

    static String get(String url, String mediaType, int sizeLimit) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Lookup cancelled");
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
                return new String(data.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
