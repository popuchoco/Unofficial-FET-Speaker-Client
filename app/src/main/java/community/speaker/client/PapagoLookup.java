package community.speaker.client;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class PapagoLookup {
    static final String SOURCE = "https://papago.naver.com/";
    private static final String ENDPOINT = "https://papago.apigw.ntruss.com/nmt/v1/translation";
    private static final String USER_AGENT =
            "UnofficialFETSpeakerClient/0.1 (https://github.com/popuchoco/Unofficial-FET-Speaker-Client)";
    private static final int RESPONSE_LIMIT = 64 * 1024;

    private PapagoLookup() {}

    static LookupAnswer search(TranslationRequest request, String clientId, String clientSecret)
            throws IOException, JSONException {
        if (clientId == null || clientId.trim().isEmpty()
                || clientSecret == null || clientSecret.trim().isEmpty()
                || hasHeaderBreak(clientId) || hasHeaderBreak(clientSecret)) {
            throw new IOException("Papago credentials are missing or invalid");
        }
        if (request.targetCode == null || request.text.length() == 0) {
            throw new IOException("Papago translation request is invalid");
        }

        byte[] body = ("source=auto&target=" + encode(request.targetCode)
                + "&text=" + encode(request.text)).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("X-NCP-APIGW-API-KEY-ID", clientId.trim());
        connection.setRequestProperty("X-NCP-APIGW-API-KEY", clientSecret.trim());
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Papago returned HTTP " + responseCode);
            }
            String json;
            try (InputStream input = connection.getInputStream()) {
                json = readLimited(input);
            }
            return answer(json, request);
        } finally {
            connection.disconnect();
        }
    }

    static LookupAnswer answer(String json, TranslationRequest request) throws JSONException, IOException {
        String translated = new JSONObject(json).getJSONObject("message")
                .getJSONObject("result").getString("translatedText").trim();
        if (translated.isEmpty()) throw new IOException("Papago returned an empty translation");
        return new LookupAnswer("Papago 翻譯：" + translated, SOURCE, translated, request.ttsLocale);
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static boolean hasHeaderBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static String readLimited(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > RESPONSE_LIMIT) throw new IOException("Papago response is too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
