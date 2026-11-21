package community.speaker.client;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ChatGptProvider {
    private static final String AUTH_ENDPOINT = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN_ENDPOINT = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS_ENDPOINT = "https://auth.openai.com/.well-known/jwks.json";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String MODELS_ENDPOINT = RESOURCE + "/models";
    private static final String RESPONSES_ENDPOINT = RESOURCE + "/responses";
    private static final String ISSUER = "https://auth.openai.com";
    private static final String REQUIRED_SCOPES = "offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String APP_NAME = "愛狐說";
    private final ChatGptCredentialStore store;
    private final Object credentialsLock = new Object();
    private final Object refreshLock = new Object();

    ChatGptProvider(Context context) {
        store = new ChatGptCredentialStore(context.getApplicationContext());
    }

    boolean isConnected() {
        synchronized (credentialsLock) {
            try {
                JSONObject saved = store.load();
                return !saved.optString("client_id").isEmpty()
                        && !saved.optString("refresh_token").isEmpty()
                        && ChatGptProtocol.hasUsageScopes(saved.optString("scope"));
            } catch (IOException error) {
                return false;
            }
        }
    }

    String selectedModelName() {
        synchronized (credentialsLock) {
            try {
                return store.load().optString("model_name", "");
            } catch (IOException error) {
                return "";
            }
        }
    }

    String selectedModelSlug() {
        synchronized (credentialsLock) {
            try {
                return store.load().optString("model_slug", "");
            } catch (IOException error) {
                return "";
            }
        }
    }

    AuthorizationRequest beginAuthorization() throws IOException {
        JSONObject saved;
        synchronized (credentialsLock) {
            saved = store.load();
        }
        String registeredClientId = saved.optString("client_id", "");
        boolean firstRegistration = registeredClientId.isEmpty();
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        listener.setSoTimeout(180_000);
        String redirectUri = "http://127.0.0.1:" + listener.getLocalPort() + "/auth/callback";
        String state = randomToken(32);
        String nonce = randomToken(32);
        String verifier = randomToken(32);
        String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        Uri.Builder url = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", firstRegistration ? "dynamic_agent_client" : registeredClientId)
                .appendQueryParameter("ext_agent_host_id", store.hostId())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", "openid profile email " + REQUIRED_SCOPES)
                .appendQueryParameter("resource", RESOURCE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge);
        if (firstRegistration) {
            url.appendQueryParameter("agent_name_hint", APP_NAME);
        } else {
            String idToken = saved.optString("id_token", "");
            if (!idToken.isEmpty()) url.appendQueryParameter("id_token_hint", idToken);
        }
        return new AuthorizationRequest(listener, url.build().toString(), redirectUri,
                state, nonce, verifier, registeredClientId, firstRegistration);
    }

    void finishAuthorization(AuthorizationRequest attempt) throws IOException {
        try {
            ChatGptProtocol.Callback callback = attempt.receiveCallback();
            JSONObject token = exchangeCode(attempt, callback);
            String grantedScopes = token.optString("scope");
            if (!ChatGptProtocol.hasUsageScopes(grantedScopes)) {
                throw new IOException("尚未允許 ChatGPT 方案用量。請重新登入並同意方案用量權限。");
            }
            String idToken = token.optString("id_token");
            String subject = validateIdToken(idToken, callback.clientId, attempt.nonce);
            String accessToken = token.optString("access_token");
            String refreshToken = token.optString("refresh_token");
            long expiresIn = token.optLong("expires_in", 0);
            if (accessToken.isEmpty() || refreshToken.isEmpty() || expiresIn <= 0) {
                throw new IOException("ChatGPT 沒有傳回完整的登入授權。");
            }
            JSONObject credentials = new JSONObject()
                    .put("client_id", callback.clientId)
                    .put("host_id", store.hostId())
                    .put("subject", subject)
                    .put("id_token", idToken)
                    .put("access_token", accessToken)
                    .put("refresh_token", refreshToken)
                    .put("scope", grantedScopes)
                    .put("expires_at", System.currentTimeMillis() / 1000L + expiresIn);
            synchronized (credentialsLock) {
                store.save(credentials);
            }
        } catch (JSONException error) {
            throw new IOException("ChatGPT 登入資料格式無法讀取。", error);
        } finally {
            attempt.close();
        }
    }

    void clearConnection() {
        synchronized (credentialsLock) {
            store.clear();
        }
    }

    List<Model> listModels() throws IOException {
        String token = freshAccessToken();
        HttpURLConnection connection = open(MODELS_ENDPOINT, "GET", token);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw responseError(connection, status);
            JSONObject response = new JSONObject(read(connection.getInputStream(), 512 * 1024));
            JSONArray models = response.optJSONArray("models");
            if (models == null) throw new IOException("無法取得 ChatGPT 可用模型清單。");
            List<Model> results = new ArrayList<>();
            for (int index = 0; index < models.length(); index++) {
                JSONObject model = models.optJSONObject(index);
                if (model == null || !"list".equals(model.optString("visibility"))) continue;
                String slug = model.optString("slug");
                String name = model.optString("display_name");
                if (!slug.isEmpty() && !name.isEmpty()) results.add(new Model(slug, name));
            }
            if (results.isEmpty()) throw new IOException("ChatGPT 帳戶目前沒有可用的對話模型。");
            return results;
        } catch (JSONException error) {
            throw new IOException("ChatGPT 模型清單格式無法讀取。", error);
        } finally {
            connection.disconnect();
        }
    }

    void selectModel(Model model) throws IOException {
        synchronized (credentialsLock) {
            try {
                JSONObject saved = store.load();
                saved.put("model_slug", model.slug).put("model_name", model.name);
                store.save(saved);
            } catch (JSONException error) {
                throw new IOException("無法儲存 ChatGPT 模型選擇。", error);
            }
        }
    }

    String respond(String model, List<Message> messages) throws IOException {
        String accessToken = freshAccessToken();
        HttpURLConnection connection = open(RESPONSES_ENDPOINT, "POST", accessToken);
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        try {
            JSONObject request = new JSONObject()
                    .put("model", model)
                    .put("instructions", "你是愛狐說的語音助理。用繁體中文回答，直接說重點，避免表格和程式碼，適合由音箱朗讀。回答簡短但要完整；問題不清楚時先簡短追問。")
                    .put("max_output_tokens", 1200)
                    .put("store", false)
                    .put("stream", true);
            JSONArray input = new JSONArray();
            for (Message message : messages) {
                input.put(new JSONObject().put("role", message.role).put("content", message.content));
            }
            request.put("input", input);
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw responseError(connection, status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                return ChatGptProtocol.readCompletedResponse(reader);
            }
        } catch (JSONException error) {
            throw new IOException("無法建立 ChatGPT 請求。", error);
        } finally {
            connection.disconnect();
        }
    }

    private String freshAccessToken() throws IOException {
        synchronized (refreshLock) {
            JSONObject credentials;
            synchronized (credentialsLock) {
                credentials = store.load();
            }
            String accessToken = credentials.optString("access_token");
            long expiresAt = credentials.optLong("expires_at", 0);
            if (accessToken.isEmpty() || expiresAt <= System.currentTimeMillis() / 1000L + 60) {
                return refresh(credentials);
            }
            return accessToken;
        }
    }

    private String refresh(JSONObject credentials) throws IOException {
        String clientId = credentials.optString("client_id");
        String refreshToken = credentials.optString("refresh_token");
        if (clientId.isEmpty() || refreshToken.isEmpty()) {
            throw new IOException("ChatGPT 連結已失效，請重新登入。");
        }
        JSONObject token = postForm(TOKEN_ENDPOINT, new String[][] {
                { "grant_type", "refresh_token" },
                { "client_id", clientId },
                { "refresh_token", refreshToken },
                { "resource", RESOURCE }
        });
        String access = token.optString("access_token");
        long expiresIn = token.optLong("expires_in", 0);
        if (access.isEmpty() || expiresIn <= 0) throw new IOException("ChatGPT 連結已失效，請重新登入。");
        try {
            JSONObject updated;
            synchronized (credentialsLock) {
                updated = store.load();
                if (!clientId.equals(updated.optString("client_id"))
                        || !refreshToken.equals(updated.optString("refresh_token"))) {
                    throw new IOException("ChatGPT 連結已變更，請重新送出問題。");
                }
                updated.put("access_token", access)
                        .put("expires_at", System.currentTimeMillis() / 1000L + expiresIn);
                if (!token.optString("refresh_token").isEmpty()) {
                    updated.put("refresh_token", token.optString("refresh_token"));
                }
                if (!token.optString("scope").isEmpty()) updated.put("scope", token.optString("scope"));
                if (!ChatGptProtocol.hasUsageScopes(updated.optString("scope"))) {
                    throw new IOException("ChatGPT 方案用量授權已失效，請重新登入。");
                }
                store.save(updated);
            }
            return access;
        } catch (JSONException error) {
            throw new IOException("無法更新 ChatGPT 連結。", error);
        }
    }

    private JSONObject exchangeCode(AuthorizationRequest attempt, ChatGptProtocol.Callback callback) throws IOException {
        return postForm(TOKEN_ENDPOINT, new String[][] {
                { "grant_type", "authorization_code" },
                { "client_id", callback.clientId },
                { "code", callback.code },
                { "code_verifier", attempt.verifier },
                { "redirect_uri", attempt.redirectUri },
                { "resource", RESOURCE }
        });
    }

    private JSONObject postForm(String endpoint, String[][] fields) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "UnofficialFETSpeakerClient/0.1");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        connection.setDoOutput(true);
        StringBuilder body = new StringBuilder();
        for (String[] field : fields) {
            if (body.length() > 0) body.append('&');
            body.append(encode(field[0])).append('=').append(encode(field[1]));
        }
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw responseError(connection, status);
            return new JSONObject(read(connection.getInputStream(), 64 * 1024));
        } catch (JSONException error) {
            throw new IOException("ChatGPT 登入回應格式無法讀取。", error);
        } finally {
            connection.disconnect();
        }
    }

    private String validateIdToken(String jwt, String clientId, String nonce) throws IOException {
        String[] parts = jwt.split("\\.", -1);
        if (parts.length != 3) throw new IOException("ChatGPT 身分授權格式無效。");
        try {
            JSONObject header = new JSONObject(new String(decodeUrl(parts[0]), StandardCharsets.UTF_8));
            JSONObject claims = new JSONObject(new String(decodeUrl(parts[1]), StandardCharsets.UTF_8));
            if (!"RS256".equals(header.optString("alg"))) throw new IOException("ChatGPT 身分授權簽章格式無效。");
            verifyJws(parts[0] + "." + parts[1], decodeUrl(parts[2]), header.optString("kid"));
            if (!ISSUER.equals(claims.optString("iss"))
                    || !hasAudience(claims.opt("aud"), clientId)
                    || claims.optLong("exp", 0) <= System.currentTimeMillis() / 1000L
                    || !constantTimeEquals(nonce, claims.optString("nonce"))) {
                throw new IOException("ChatGPT 身分授權驗證失敗，請重新登入。");
            }
            String subject = claims.optString("sub");
            if (subject.isEmpty()) throw new IOException("ChatGPT 身分授權缺少帳戶識別碼。");
            return subject;
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("ChatGPT 身分授權格式無效。", error);
        }
    }

    private void verifyJws(String input, byte[] signatureBytes, String keyId) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) open(JWKS_ENDPOINT, "GET", null);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("無法驗證 ChatGPT 登入簽章。");
            JSONObject response = new JSONObject(read(connection.getInputStream(), 128 * 1024));
            JSONArray keys = response.optJSONArray("keys");
            if (keys == null) throw new IOException("無法驗證 ChatGPT 登入簽章。");
            JSONObject match = null;
            for (int i = 0; i < keys.length(); i++) {
                JSONObject candidate = keys.optJSONObject(i);
                if (candidate != null && keyId.equals(candidate.optString("kid"))
                        && "RSA".equals(candidate.optString("kty"))) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) throw new IOException("找不到 ChatGPT 登入簽章金鑰。");
            BigInteger modulus = new BigInteger(1, decodeUrl(match.getString("n")));
            BigInteger exponent = new BigInteger(1, decodeUrl(match.getString("e")));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(input.getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signatureBytes)) throw new IOException("ChatGPT 登入簽章驗證失敗。");
        } catch (JSONException | GeneralSecurityException | IllegalArgumentException error) {
            throw new IOException("無法驗證 ChatGPT 登入簽章。", error);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String endpoint, String method, String accessToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout("POST".equals(method) ? 90_000 : 20_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "UnofficialFETSpeakerClient/0.1");
        connection.setRequestProperty("Accept", "application/json");
        if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        return connection;
    }

    private static IOException responseError(HttpURLConnection connection, int status) {
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return new IOException("ChatGPT 連結已失效，請重新登入。");
        }
        if (status == HttpURLConnection.HTTP_FORBIDDEN || status == 429) {
            return new IOException("ChatGPT 目前無法使用方案用量，請檢查帳戶授權或稍後再試。");
        }
        return new IOException("ChatGPT 服務暫時無法使用（" + status + "），請稍後再試。");
    }

    private static String read(InputStream input, int maxBytes) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("ChatGPT 請求已取消。");
                if (data.size() + count > maxBytes) throw new IOException("ChatGPT 回應超過大小限制。");
                data.write(buffer, 0, count);
            }
            return new String(data.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IOException("無法建立 ChatGPT 登入請求。", impossible);
        }
    }

    private static String randomToken(int byteCount) {
        byte[] value = new byte[byteCount];
        new SecureRandom().nextBytes(value);
        return base64Url(value);
    }

    private static String base64Url(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] decodeUrl(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] sha256(byte[] value) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException error) {
            throw new IOException("無法準備 ChatGPT 安全登入。", error);
        }
    }

    private static boolean hasAudience(Object audience, String expected) {
        if (audience instanceof String) return expected.equals(audience);
        if (audience instanceof JSONArray) {
            JSONArray values = (JSONArray) audience;
            for (int i = 0; i < values.length(); i++) if (expected.equals(values.optString(i))) return true;
        }
        return false;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    final class AuthorizationRequest implements AutoCloseable {
        private final ServerSocket listener;
        final String authorizationUrl;
        final String redirectUri;
        final String nonce;
        final String verifier;
        final String registeredClientId;
        final boolean firstRegistration;
        private final String state;

        AuthorizationRequest(ServerSocket listener, String authorizationUrl, String redirectUri,
                             String state, String nonce, String verifier,
                             String registeredClientId, boolean firstRegistration) {
            this.listener = listener;
            this.authorizationUrl = authorizationUrl;
            this.redirectUri = redirectUri;
            this.state = state;
            this.nonce = nonce;
            this.verifier = verifier;
            this.registeredClientId = registeredClientId;
            this.firstRegistration = firstRegistration;
        }

        ChatGptProtocol.Callback receiveCallback() throws IOException {
            try (ServerSocket server = listener; Socket client = server.accept()) {
                if (!client.getInetAddress().isLoopbackAddress()) throw new IOException("ChatGPT 回呼來源無效。");
                client.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        client.getInputStream(), StandardCharsets.US_ASCII));
                String requestLine = reader.readLine();
                int bytes = requestLine == null ? 0 : requestLine.length();
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    bytes += header.length();
                    if (bytes > 8192) throw new IOException("ChatGPT 回呼標頭過長。");
                }
                try {
                    ChatGptProtocol.Callback callback = ChatGptProtocol.parseCallback(
                            requestLine, state, registeredClientId, firstRegistration);
                    writeBrowserResponse(client, "200 OK", "已完成 ChatGPT 登入，請返回愛狐說。\n");
                    return callback;
                } catch (IOException invalid) {
                    try {
                        writeBrowserResponse(client, "400 Bad Request", "登入回呼無效，請返回愛狐說重新嘗試。\n");
                    } catch (IOException ignored) { }
                    throw invalid;
                }
            } catch (java.net.SocketTimeoutException timeout) {
                throw new IOException("ChatGPT 登入逾時，請重新嘗試。", timeout);
            }
        }

        private void writeBrowserResponse(Socket client, String status, String body) throws IOException {
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + content.length + "\r\nConnection: close\r\n\r\n";
            client.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().write(content);
            client.getOutputStream().flush();
        }

        @Override public void close() {
            try { listener.close(); } catch (IOException ignored) { }
        }
    }

    static final class Model {
        final String slug;
        final String name;

        Model(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }

        @Override public String toString() { return name; }
    }

    static final class Message {
        final String role;
        final String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
