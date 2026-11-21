package community.speaker.client;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatGptProtocolTest {
    @Test public void firstLoginRequiresTheProviderIssuedClientIdAndMatchingState() throws IOException {
        ChatGptProtocol.Callback callback = ChatGptProtocol.parseCallback(
                "GET /auth/callback?code=abc%2B123&state=safe&client_id=oaiapp_local HTTP/1.1",
                "safe", null, true);

        assertEquals("abc+123", callback.code);
        assertEquals("oaiapp_local", callback.clientId);
    }

    @Test public void returningLoginCannotSwitchTheRegisteredClient() {
        assertThrows(IOException.class, () -> ChatGptProtocol.parseCallback(
                "GET /auth/callback?code=abc&state=safe&client_id=oaiapp_other HTTP/1.1",
                "safe", "oaiapp_saved", false));
        assertThrows(IOException.class, () -> ChatGptProtocol.parseCallback(
                "GET /auth/callback?code=abc&state=wrong&client_id=oaiapp_saved HTTP/1.1",
                "safe", "oaiapp_saved", false));
    }

    @Test public void callbackMustUseTheExpectedLocalPathAndCannotRepeatSecurityParameters() {
        assertThrows(IOException.class, () -> ChatGptProtocol.parseCallback(
                "GET http://127.0.0.1/auth/callback?code=abc&state=safe&client_id=oaiapp_local HTTP/1.1",
                "safe", null, true));
        assertThrows(IOException.class, () -> ChatGptProtocol.parseCallback(
                "GET /auth/callback?code=abc&state=safe&state=safe&client_id=oaiapp_local HTTP/1.1",
                "safe", null, true));
        assertThrows(IOException.class, () -> ChatGptProtocol.parseCallback(
                "GET /auth/callback?code=%ZZ&state=safe&client_id=oaiapp_local HTTP/1.1",
                "safe", null, true));
    }

    @Test public void planUseRequiresBothProviderScopes() {
        assertTrue(ChatGptProtocol.hasUsageScopes("openid offline_access resource.invoke chatgpt.tokens.use.direct"));
        assertFalse(ChatGptProtocol.hasUsageScopes("openid offline_access resource.invoke"));
    }

    @Test public void onlyACompletedStreamReturnsAnAnswer() throws IOException {
        String events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\n\n"
                + "data: {\"type\":\"response.completed\"}\n\n";

        assertEquals("你好", ChatGptProtocol.readCompletedResponse(new BufferedReader(new StringReader(events))));
        assertThrows(IOException.class, () -> ChatGptProtocol.readCompletedResponse(
                new BufferedReader(new StringReader("data: {\"type\":\"response.output_text.delta\",\"delta\":\"半段\"}\n"))));
    }

    private static <T extends Throwable> T assertThrows(Class<T> type, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return type.cast(error);
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private interface ThrowingRunnable { void run() throws Throwable; }
}
