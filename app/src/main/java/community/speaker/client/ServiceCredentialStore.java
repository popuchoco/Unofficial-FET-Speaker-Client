package community.speaker.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ServiceCredentialStore {
    static final String CWA_API_KEY = "cwa_api_key";
    static final String MOENV_API_KEY = "moenv_api_key";
    static final String PAPAGO_CLIENT_SECRET = "papago_client_secret";
    private static final String PREFS = "service_credentials";
    private static final String ENCRYPTED = "encrypted_values";
    private static final String KEY_ALIAS = "community.speaker.client.service.credentials";
    private static final String[] LEGACY_KEYS = {
            CWA_API_KEY, MOENV_API_KEY, PAPAGO_CLIENT_SECRET
    };

    private final SharedPreferences preferences;

    ServiceCredentialStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String get(String name) throws IOException {
        return load().get(name);
    }

    synchronized void save(String name, String value) throws IOException {
        ServiceCredentials updated;
        try {
            updated = load().with(name, value);
        } catch (JSONException error) {
            throw new IOException("無法更新服務憑證。", error);
        }
        store(updated);
    }

    synchronized void migrate(SharedPreferences legacy) throws IOException {
        ServiceCredentials credentials = load();
        boolean changed = false;
        Map<String, ?> oldValues = legacy.getAll();
        for (String name : LEGACY_KEYS) {
            Object oldValue = oldValues.get(name);
            if (!credentials.contains(name) && oldValue instanceof String) {
                String value = ((String) oldValue).trim();
                if (!value.isEmpty()) {
                    try {
                        credentials = credentials.with(name, value);
                    } catch (JSONException error) {
                        throw new IOException("無法準備服務憑證遷移。", error);
                    }
                    changed = true;
                }
            }
        }
        if (changed) store(credentials);
        SharedPreferences.Editor editor = legacy.edit();
        for (String name : LEGACY_KEYS) editor.remove(name);
        if (!editor.commit()) throw new IOException("無法完成服務憑證遷移。");
    }

    private ServiceCredentials load() throws IOException {
        String encoded = preferences.getString(ENCRYPTED, null);
        if (encoded == null) return ServiceCredentials.empty();
        try {
            byte[] encrypted = Base64.decode(encoded, Base64.NO_WRAP);
            if (encrypted.length < 12 + 16) throw new IOException("Saved service credentials are incomplete.");
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[encrypted.length - iv.length];
            System.arraycopy(encrypted, 0, iv, 0, iv.length);
            System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return ServiceCredentials.parse(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | JSONException | IllegalArgumentException error) {
            throw new IOException("無法讀取此手機上的服務憑證，請重新設定。", error);
        }
    }

    private void store(ServiceCredentials credentials) throws IOException {
        if (credentials.isEmpty()) {
            if (!preferences.edit().remove(ENCRYPTED).commit()) {
                throw new IOException("無法移除服務憑證。");
            }
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(credentials.toJson().getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            String encoded = Base64.encodeToString(result, Base64.NO_WRAP);
            if (!preferences.edit().putString(ENCRYPTED, encoded).commit()) {
                throw new IOException("無法安全保存服務憑證。");
            }
        } catch (GeneralSecurityException error) {
            throw new IOException("無法安全保存服務憑證。", error);
        }
    }

    private SecretKey key() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (IOException | java.security.cert.CertificateException error) {
            throw new GeneralSecurityException("Android Keystore unavailable", error);
        }
    }
}
