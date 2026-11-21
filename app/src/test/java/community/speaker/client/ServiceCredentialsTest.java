package community.speaker.client;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceCredentialsTest {
    @Test public void serializesCredentialsAndRemovesBlankValues() throws JSONException {
        ServiceCredentials credentials = ServiceCredentials.empty()
                .with(ServiceCredentialStore.CWA_API_KEY, " cwa-key ")
                .with(ServiceCredentialStore.PAPAGO_CLIENT_SECRET, "papago-secret");

        ServiceCredentials restored = ServiceCredentials.parse(credentials.toJson());
        assertEquals("cwa-key", restored.get(ServiceCredentialStore.CWA_API_KEY));
        assertEquals("papago-secret", restored.get(ServiceCredentialStore.PAPAGO_CLIENT_SECRET));
        assertTrue(restored.contains(ServiceCredentialStore.CWA_API_KEY));

        ServiceCredentials cleared = restored.with(ServiceCredentialStore.CWA_API_KEY, "  ");
        assertFalse(cleared.contains(ServiceCredentialStore.CWA_API_KEY));
        assertEquals("papago-secret", cleared.get(ServiceCredentialStore.PAPAGO_CLIENT_SECRET));
    }

    @Test public void emptyCredentialsHaveNoPayloadToPersist() throws JSONException {
        ServiceCredentials credentials = ServiceCredentials.empty()
                .with(ServiceCredentialStore.MOENV_API_KEY, "moenv-key")
                .with(ServiceCredentialStore.MOENV_API_KEY, "");

        assertTrue(credentials.isEmpty());
        assertEquals("{}", credentials.toJson());
    }
}
