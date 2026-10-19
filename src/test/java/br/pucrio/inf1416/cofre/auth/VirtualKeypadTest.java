/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VirtualKeypadTest {
    @Test void everyLayoutCoversAllTenDigitsOnceAndCapturesBeforeReshuffle() {
        VirtualKeypad keypad = new VirtualKeypad();
        for (int round = 0; round < 100; round++) {
            keypad.clear();
            for (int click = 0; click < 10; click++) {
                var before = keypad.layout(); var digits = new HashSet<Character>();
                for (DigitPair pair : before) { assertTrue(digits.add(pair.first())); assertTrue(digits.add(pair.second())); }
                assertEquals(10, digits.size());
                int selected = click % 5; keypad.press(selected);
                assertEquals(before.get(selected), keypad.captured().get(click));
                assertNotSame(before, keypad.layout());
            }
            assertThrows(IllegalStateException.class, () -> keypad.press(0));
        }
    }
    @Test void clearAndSubmissionBoundaries() {
        var keypad = new VirtualKeypad();
        for (int i = 0; i < 10; i++) { assertEquals(i >= 8, keypad.canSubmit()); keypad.press(0); }
        assertTrue(keypad.canSubmit()); keypad.clear(); assertEquals(0, keypad.length()); assertFalse(keypad.canSubmit());
        assertThrows(UnsupportedOperationException.class, () -> keypad.layout().clear());
    }
    @Test void invalidPairsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DigitPair('1','1'));
        assertThrows(IllegalArgumentException.class, () -> new DigitPair('a','2'));
    }
}
