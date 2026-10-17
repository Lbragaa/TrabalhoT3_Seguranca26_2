package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.crypto.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Explicit performance experiment, never an automatic timing-sensitive unit test or login path. */
public final class KeypadBenchmark {
    private KeypadBenchmark() { }
    public static void main(String[] args) throws Exception {
        int repetitions = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        if (repetitions < 1 || repetitions > 10) { throw new IllegalArgumentException("Use 1–10 repetitions."); }
        Path output = Path.of(args.length > 1 ? args[1] : "target/keypad-benchmark.csv");
        Files.createDirectories(output.toAbsolutePath().getParent());
        StringBuilder csv = new StringBuilder("digits,workers,candidates_checked,min_ms,median_ms,max_ms,scenario\n");
        SecureRandom random = new SecureRandom();
        char[] digits = "0123456789".toCharArray();
        for (int i = digits.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1); char swap = digits[i]; digits[i] = digits[j]; digits[j] = swap;
        }
        int parallelism = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        System.out.println("JVM=" + System.getProperty("java.version") + "; availableProcessors="
                + Runtime.getRuntime().availableProcessors() + "; BCrypt=2y/08; full scans; no secrets printed");
        // Warm up the existing BC wrapper without outputting the disposable password/hash.
        char[] warm = RegistrationTestSupport.password();
        try { String hash = new PasswordHasher().hash(warm); for (int i = 0; i < 32; i++) { new PasswordHasher().verify(hash, warm); } }
        finally { Arrays.fill(warm, '\0'); }
        for (int length : new int[]{8, 9, 10}) {
            for (String scenario : List.of("worst-correct-last", "representative-correct")) {
                char[][] pairs = new char[length][2];
                char[] password = new char[length];
                for (int i = 0; i < length; i++) {
                    // Adjacent disjoint pairs give the maximum possible eligible count 2^length.
                    password[i] = digits[2 * (i % 5) + 1];
                    pairs[i][1] = password[i];
                    if (scenario.startsWith("worst")) { pairs[i][0] = digits[2 * (i % 5)]; }
                    else { do { pairs[i][0] = digits[random.nextInt(10)]; } while (pairs[i][0] == password[i]); }
                }
                String hash;
                try { hash = new PasswordHasher().hash(password); }
                finally { Arrays.fill(password, '\0'); }
                for (int workers : parallelism == 1 ? new int[]{1} : new int[]{1, parallelism}) {
                    var verifier = new KeypadPasswordVerifier(workers);
                    double[] times = new double[repetitions];
                    int checked = 0;
                    try {
                        for (int run = 0; run < repetitions; run++) {
                            List<DigitPair> captured = new ArrayList<>();
                            for (char[] pair : pairs) { captured.add(new DigitPair(pair[0], pair[1])); }
                            long start = System.nanoTime();
                            int matched;
                            try (var result = verifier.verify(hash, captured)) {
                                checked = result.candidatesChecked(); matched = result.matched() ? 1 : 0;
                            }
                            times[run] = (System.nanoTime() - start) / 1_000_000.0;
                            if (matched != 1 || (scenario.startsWith("worst") && checked != 1 << length)) {
                                throw new IllegalStateException("Benchmark verification/count failed.");
                            }
                        }
                    } finally { verifier.close(); }
                    Arrays.sort(times);
                    String row = String.format(Locale.ROOT, "%d,%d,%d,%.1f,%.1f,%.1f,%s%n",
                            length, workers, checked, times[0], times[times.length / 2], times[times.length - 1], scenario);
                    csv.append(row); Files.writeString(output, csv, StandardCharsets.UTF_8);
                    System.out.print(row);
                }
            }
        }
    }
}
