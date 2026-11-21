package community.speaker.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ChatGptCredentialStore {
    private static final String PREFS = "chatgpt_local_connection";
    private static final String CREDENTIALS = "encrypted_credentials";
    private static final String HOST_ID = "host_id";
    private static final String KEY_ALIAS = "community.speaker.client.chatgpt.tokens";
    private final SharedPreferences preferences;

    ChatGptCredentialStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String hostId() {
        String value = preferences.getString(HOST_ID, null);
        if (value == null) {
            value = "urn:uuid:" + UUID.randomUUID();
            preferences.edit().putString(HOST_ID, value).apply();
        }
        return value;
    }

    synchronized JSONObject load() throws IOException {
        String encoded = preferences.getString(CREDENTIALS, null);
        if (encoded == null) return new JSONObject();
        try {
            byte[] encrypted = Base64.decode(encoded, Base64.NO_WRAP);
            if (encrypted.length < 12 + 16) throw new IOException("Saved ChatGPT connection is incomplete.");
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[encrypted.length - iv.length];
            System.arraycopy(encrypted, 0, iv, 0, iv.length);
            System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | JSONException | IllegalArgumentException error) {
            throw new IOException("無法讀取此手機上的 ChatGPT 連結，請重新登入。", error);
        }
    }

    synchronized void save(JSONObject credentials) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(credentials.toString().getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            preferences.edit().putString(CREDENTIALS, Base64.encodeToString(result, Base64.NO_WRAP)).apply();
        } catch (GeneralSecurityException error) {
            throw new IOException("無法安全保存 ChatGPT 連結。", error);
        }
    }

    synchronized void clear() {
        preferences.edit().remove(CREDENTIALS).apply();
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
        } catch (IOException error) {
            throw new GeneralSecurityException("Android Keystore unavailable", error);
        } catch (java.security.cert.CertificateException error) {
            throw new GeneralSecurityException("Android Keystore unavailable", error);
        }
    }
}
