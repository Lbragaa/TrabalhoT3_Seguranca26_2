package br.pucrio.inf1416.cofre.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/** Byte-oriented parsing avoids creating an immutable String containing a decrypted key. */
final class PemSupport {
    private PemSupport() { }

    static byte[] decode(byte[] input, String label) throws GeneralSecurityException {
        byte[] begin = ("-----BEGIN " + label + "-----").getBytes(StandardCharsets.US_ASCII);
        byte[] end = ("-----END " + label + "-----").getBytes(StandardCharsets.US_ASCII);
        int start = find(input, begin, 0);
        int finish = start < 0 ? -1 : find(input, end, start + begin.length);
        if (start < 0 || finish < 0 || find(input, begin, start + begin.length) >= 0) {
            throw new GeneralSecurityException("Expected exactly one " + label + " PEM block.");
        }
        byte[] base64 = new byte[finish - start - begin.length];
        int count = 0;
        try {
            for (int i = start + begin.length; i < finish; i++) {
                byte b = input[i];
                if (b != ' ' && b != '\r' && b != '\n' && b != '\t') { base64[count++] = b; }
            }
            byte[] compact = Arrays.copyOf(base64, count);
            try {
                return Base64.getDecoder().decode(compact);
            } finally {
                Arrays.fill(compact, (byte) 0);
            }
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid " + label + " PEM encoding.");
        } finally {
            Arrays.fill(base64, (byte) 0);
        }
    }

    private static int find(byte[] input, byte[] marker, int offset) {
        outer: for (int i = offset; i <= input.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (input[i + j] != marker[j]) { continue outer; }
            }
            return i;
        }
        return -1;
    }
}
