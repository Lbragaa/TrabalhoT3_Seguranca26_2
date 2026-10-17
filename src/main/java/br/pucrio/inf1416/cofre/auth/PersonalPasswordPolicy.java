package br.pucrio.inf1416.cofre.auth;

/** Agreed provisional interpretation: ASCII digits, 8–10 positions, no adjacent repeats. */
public final class PersonalPasswordPolicy {
    private PersonalPasswordPolicy() { }
    public static boolean valid(char[] password) {
        if (password == null || password.length < 8 || password.length > 10) { return false; }
        for (int i = 0; i < password.length; i++) {
            if (password[i] < '0' || password[i] > '9' || (i > 0 && password[i] == password[i - 1])) { return false; }
        }
        return true;
    }
}
