package community.speaker.client;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ChatGptProtocol {
    private static final int MAX_CALLBACK_LINE = 8192;
    private static final int MAX_ANSWER_CHARS = 6000;

    private ChatGptProtocol() {}

    static Callback parseCallback(String requestLine, String expectedState,
                                  String registeredClientId, boolean firstRegistration) throws IOException {
        if (requestLine == null || requestLine.length() > MAX_CALLBACK_LINE) {
            throw new IOException("ChatGPT 登入回呼無效。");
        }
        String[] request = requestLine.split(" ", 3);
        if (request.length != 3 || !"GET".equals(request[0]) || !request[2].startsWith("HTTP/1.")) {
            throw new IOException("ChatGPT 登入回呼無效。");
        }
        URI uri;
        try {
            uri = new URI(request[1]);
        } catch (java.net.URISyntaxException invalid) {
            throw new IOException("ChatGPT 登入回呼無效。", invalid);
        }
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                || !"/auth/callback".equals(uri.getRawPath())) {
            throw new IOException("ChatGPT 登入回呼路徑不符。");
        }
        Map<String, String> values = parseQuery(uri.getRawQuery());
        if (!constantTimeEquals(expectedState, values.get("state"))) {
            throw new IOException("ChatGPT 登入狀態不符，請重新連結。");
        }
        if (values.containsKey("error")) {
            throw new IOException("已取消 ChatGPT 登入，沒有取得授權。");
        }
        String code = values.get("code");
        if (code == null || code.isEmpty() || code.length() > 4096) {
            throw new IOException("ChatGPT 沒有傳回授權碼。");
        }

        String returnedClientId = values.get("client_id");
        String clientId;
        if (firstRegistration) {
            if (returnedClientId == null || returnedClientId.isEmpty()
                    || "dynamic_agent_client".equals(returnedClientId)) {
                throw new IOException("ChatGPT 尚未完成此 App 的用戶端登記。");
            }
            clientId = returnedClientId;
        } else {
            if (registeredClientId == null || registeredClientId.isEmpty()
                    || (returnedClientId != null && !registeredClientId.equals(returnedClientId))) {
                throw new IOException("ChatGPT 登入帳戶與已連結帳戶不符。");
            }
            clientId = registeredClientId;
        }
        return new Callback(code, clientId);
    }

    static boolean hasUsageScopes(String scopes) {
        Set<String> parsed = new HashSet<>();
        if (scopes != null) {
            for (String scope : scopes.trim().split("\\s+")) {
                if (!scope.isEmpty()) parsed.add(scope);
            }
        }
        return parsed.contains("resource.invoke") && parsed.contains("chatgpt.tokens.use.direct");
    }

    static String readCompletedResponse(BufferedReader events) throws IOException {
        StringBuilder answer = new StringBuilder();
        boolean completed = false;
        String line;
        while ((line = events.readLine()) != null) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("ChatGPT 請求已取消。");
            if (!line.startsWith("data:")) continue;
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
            JSONObject event;
            try {
                event = new JSONObject(payload);
            } catch (JSONException invalid) {
                throw new IOException("ChatGPT 回覆格式無法讀取。", invalid);
            }
            String type = event.optString("type");
            if ("response.output_text.delta".equals(type)) {
                answer.append(event.optString("delta"));
                if (answer.length() > MAX_ANSWER_CHARS) {
                    throw new IOException("ChatGPT 回覆過長，請再縮小問題範圍。");
                }
            } else if ("response.completed".equals(type)) {
                completed = true;
            } else if ("response.failed".equals(type) || "response.incomplete".equals(type)) {
                throw new IOException("ChatGPT 未完成這次回答，請確認方案用量或稍後重試。");
            } else if ("error".equals(type)) {
                throw new IOException("ChatGPT 請求失敗，請稍後重試。");
            }
        }
        if (!completed) throw new IOException("ChatGPT 回覆中斷，請稍後重試。");
        String result = answer.toString().trim();
        if (result.isEmpty()) throw new IOException("ChatGPT 沒有提供可朗讀的文字回答。");
        return result;
    }

    private static Map<String, String> parseQuery(String query) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.length() > MAX_CALLBACK_LINE) return values;
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = decode(separator < 0 ? pair : pair.substring(0, separator));
            String value = decode(separator < 0 ? "" : pair.substring(separator + 1));
            if (values.put(key, value) != null) throw new IOException("ChatGPT 登入回呼含有重複欄位。");
        }
        return values;
    }

    private static String decode(String value) throws IOException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IOException("無法讀取 ChatGPT 登入回呼。", impossible);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("ChatGPT 登入回呼格式無效。", malformed);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    static final class Callback {
        final String code;
        final String clientId;

        Callback(String code, String clientId) {
            this.code = code;
            this.clientId = clientId;
        }
    }
}
