package br.pucrio.inf1416.cofre.auth;

public record DigitPair(char first, char second) {
    public DigitPair {
        if (first < '0' || first > '9' || second < '0' || second > '9' || first == second) {
            throw new IllegalArgumentException("Cada botão deve conter dois dígitos distintos.");
        }
    }
    char at(int branch) { return branch == 0 ? first : second; }
    public boolean contains(char digit) { return first == digit || second == digit; }
    @Override public String toString() { return first + "    " + second; }
}
