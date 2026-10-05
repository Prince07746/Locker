package com.guardian.app.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Holds the symmetric keys. Hibernate builds {@link EmailCryptoConverter}
 * itself (not through Spring), so the converter needs a static way to reach
 * the key. {@link CryptoInitializer} populates this at startup.
 */
public final class CryptoKeys {

    private static volatile SecretKey aesKey;
    private static volatile byte[] hmacKey;

    private CryptoKeys() {}

    public static void init(String base64Aes, String base64Hmac) {
        byte[] aes = Base64.getDecoder().decode(base64Aes);
        if (aes.length != 32) {
            throw new IllegalStateException("app.crypto-key must decode to 32 bytes (AES-256)");
        }
        aesKey = new SecretKeySpec(aes, "AES");
        hmacKey = Base64.getDecoder().decode(base64Hmac);
    }

    public static SecretKey aes() {
        if (aesKey == null) throw new IllegalStateException("Crypto keys not initialized");
        return aesKey;
    }

    public static byte[] hmac() {
        if (hmacKey == null) throw new IllegalStateException("Crypto keys not initialized");
        return hmacKey;
    }
}
