package com.guardian.app.common;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomTokens {

    private static final SecureRandom RNG = new SecureRandom();

    private RandomTokens() {}

    /** High-entropy URL-safe token for per-device bearer credentials. */
    public static String opaqueToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Numeric one-time code for the guardian's inbox. */
    public static String numericCode(int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }
}
