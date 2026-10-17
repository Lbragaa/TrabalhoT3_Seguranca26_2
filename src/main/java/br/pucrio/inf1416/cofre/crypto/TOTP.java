package br.pucrio.inf1416.cofre.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

/** Assignment implementation: SHA-1, 20-byte key, six digits, exactly 30-second steps. */
public final class TOTP implements AutoCloseable {
    private final byte[] key;
    private final long timeStepInSeconds;
    private boolean closed;

    public TOTP(String base32EncodedSecret, long timeStepInSeconds) throws Exception {
        if (timeStepInSeconds != 30) { throw new IllegalArgumentException("The assignment requires 30-second steps."); }
        byte[] decoded = new Base32(Base32.Alphabet.BASE32, false, false).fromString(base32EncodedSecret);
        if (decoded == null || decoded.length != 20) {
            if (decoded != null) { for (int i = 0; i < decoded.length; i++) { decoded[i] = 0; } }
            throw new IllegalArgumentException("TOTP requires a 20-byte Base32 key.");
        }
        this.key = decoded;
        this.timeStepInSeconds = timeStepInSeconds;
    }

    private String getTOTPCodeFromHash(byte[] hash) {
        int offset = hash[hash.length - 1] & 15;
        int truncated = ((hash[offset] & 127) << 24) | ((hash[offset + 1] & 255) << 16)
                | ((hash[offset + 2] & 255) << 8) | (hash[offset + 3] & 255);
        String code = Integer.toString(truncated % 1_000_000);
        return "0".repeat(6 - code.length()) + code;
    }

    private byte[] HMAC_SHA1(byte[] counter, byte[] keyByteArray) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyByteArray, "HmacSHA1"));
            return mac.doFinal(counter);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable.");
        }
    }

    private String TOTPCode(long timeInterval) {
        if (closed) { throw new IllegalStateException("TOTP secret has been cleared."); }
        byte[] counter = new byte[8];
        for (int i = 7; i >= 0; i--) { counter[i] = (byte) timeInterval; timeInterval >>>= 8; }
        byte[] hash = HMAC_SHA1(counter, key);
        try {
            return getTOTPCodeFromHash(hash);
        } finally {
            for (int i = 0; i < hash.length; i++) { hash[i] = 0; }
        }
    }

    public String generateCode() { return generateCodeAt(new Date().getTime()); }
    public boolean validateCode(String inputTOTP) { return validateCodeAt(inputTOTP, new Date().getTime()); }

    // Numeric-time seams keep production dependencies within the assignment's restriction.
    String generateCodeAt(long epochMillis) {
        if (epochMillis < 0) { throw new IllegalArgumentException("Time must be at or after the Unix epoch."); }
        return TOTPCode(epochMillis / 1000 / timeStepInSeconds);
    }

    boolean validateCodeAt(String input, long epochMillis) {
        if (closed) { throw new IllegalStateException("TOTP secret has been cleared."); }
        if (input == null || input.length() != 6 || epochMillis < 0) { return false; }
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) < '0' || input.charAt(i) > '9') { return false; }
        }
        long interval = epochMillis / 1000 / timeStepInSeconds;
        boolean valid = false;
        for (int delta = -1; delta <= 1; delta++) {
            if (interval + delta >= 0) {
                String expected = TOTPCode(interval + delta);
                int difference = 0;
                for (int i = 0; i < 6; i++) { difference |= expected.charAt(i) ^ input.charAt(i); }
                valid |= difference == 0;
            }
        }
        return valid;
    }

    @Override public void close() {
        for (int i = 0; i < key.length; i++) { key[i] = 0; }
        closed = true;
    }
}
