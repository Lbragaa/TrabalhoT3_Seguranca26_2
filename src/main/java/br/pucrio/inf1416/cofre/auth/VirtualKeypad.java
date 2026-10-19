/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Captures the pair displayed at click time, before shuffling the next layout. */
public final class VirtualKeypad {
    private final SecureRandom random = new SecureRandom();
    private final List<DigitPair> captured = new ArrayList<>();
    private List<DigitPair> layout;
    public VirtualKeypad() { shuffle(); }
    public List<DigitPair> layout() { return layout; }
    public List<DigitPair> captured() { return List.copyOf(captured); }
    public int length() { return captured.size(); }
    public boolean canSubmit() { return length() >= 8 && length() <= 10; }
    public void press(int button) {
        if (captured.size() >= 10) { throw new IllegalStateException("Máximo de dez posições."); }
        captured.add(layout.get(button));
        shuffle();
    }
    public void clear() { captured.clear(); shuffle(); }
    private void shuffle() {
        char[] digits = "0123456789".toCharArray();
        for (int i = 9; i > 0; i--) { int j = random.nextInt(i + 1); char swap = digits[i]; digits[i] = digits[j]; digits[j] = swap; }
        List<DigitPair> next = new ArrayList<>();
        for (int i = 0; i < 10; i += 2) { next.add(new DigitPair(digits[i], digits[i + 1])); }
        layout = List.copyOf(next);
    }
}
