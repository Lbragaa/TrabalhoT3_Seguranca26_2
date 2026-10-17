package br.pucrio.inf1416.cofre.crypto;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Base32Test {
    @ParameterizedTest @CsvSource({"f,MY======", "fo,MZXQ====", "foo,MZXW6===", "foob,MZXW6YQ=", "fooba,MZXW6YTB", "foobar,MZXW6YTBOI======"})
    void rfc4648StandardVectors(String plain, String encoded) {
        var codec = new Base32(Base32.Alphabet.BASE32, true, false);
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);
        assertEquals(encoded, codec.toString(bytes));
        assertArrayEquals(bytes, codec.fromString(encoded));
        var unpadded = new Base32(Base32.Alphabet.BASE32, false, false);
        assertArrayEquals(bytes, unpadded.fromString(encoded.replace("=", "")));
    }

    @ParameterizedTest @CsvSource({"f,CO======", "fo,CPNG====", "foo,CPNMU===", "foob,CPNMUOG=", "fooba,CPNMUOJ1", "foobar,CPNMUOJ1E8======"})
    void rfc4648HexVectors(String plain, String encoded) {
        var codec = new Base32(Base32.Alphabet.BASE32HEX, true, false);
        assertEquals(encoded, codec.toString(plain.getBytes(StandardCharsets.US_ASCII)));
        assertArrayEquals(plain.getBytes(StandardCharsets.US_ASCII), codec.fromString(encoded));
    }

    @Test void emptyLowercaseAndWhitespaceFollowSuppliedSupport() {
        var codec = new Base32(Base32.Alphabet.BASE32, false, true);
        assertEquals("", codec.toString(new byte[0]));
        assertArrayEquals(new byte[0], codec.fromString(""));
        assertEquals("my", codec.toString(new byte[]{'f'}));
        assertArrayEquals(new byte[]{'f'}, codec.fromString(" m\ny \t"));
    }

    @ParameterizedTest @ValueSource(strings = {"!", "M", "MZ", "MY=", "MY=====A", "MY======A", "ＭＹ", "========", "MY======="})
    void malformedEncodingReturnsNull(String encoded) {
        assertNull(new Base32(Base32.Alphabet.BASE32, false, false).fromString(encoded));
    }

    @Test void nullAndMissingRequiredPaddingAreRejected() {
        assertNull(new Base32(Base32.Alphabet.BASE32, false, false).fromString(null));
        assertNull(new Base32(Base32.Alphabet.BASE32, true, false).fromString("MY"));
    }
}
