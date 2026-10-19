/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.crypto.PasswordHasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Full enumeration, bounded CPU parallelism, no early match exit or auxiliary password hash. */
public final class KeypadPasswordVerifier implements AutoCloseable {
    private final ExecutorService pool;
    private final int workers;
    public KeypadPasswordVerifier() { this(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1))); }
    public KeypadPasswordVerifier(int workers) {
        if (workers < 1 || workers > 4) { throw new IllegalArgumentException("Use um a quatro workers."); }
        this.workers = workers;
        pool = Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task, "cofre-bcrypt"); thread.setDaemon(true); return thread;
        });
    }
    public int workers() { return workers; }

    public synchronized Verification verify(String stored, List<DigitPair> captured) throws InterruptedException {
        List<DigitPair> pairs = List.copyOf(captured);
        if (pairs.size() < 8 || pairs.size() > 10) { throw new IllegalArgumentException("São necessárias 8 a 10 posições."); }
        List<Future<Scan>> futures = new ArrayList<>();
        List<Scan> scans = new ArrayList<>();
        boolean interrupted = false;
        RuntimeException failure = null;
        try {
            try {
                for (int worker = 0; worker < workers; worker++) {
                    int index = worker;
                    futures.add(pool.submit(() -> scan(stored, pairs, index)));
                }
            } catch (RuntimeException e) { failure = e; }
            // Drain every submitted task, including on interruption/close, before wiping matched buffers.
            for (Future<Scan> future : futures) {
                boolean finished = false;
                while (!finished) {
                    try { scans.add(future.get()); finished = true; }
                    catch (InterruptedException e) { interrupted = true; }
                    catch (ExecutionException e) { failure = new IllegalStateException("Falha na verificação BCrypt."); finished = true; }
                }
            }
            if (interrupted) { Thread.currentThread().interrupt(); throw new InterruptedException("Verificação interrompida."); }
            if (failure != null) { throw failure; }
            int checked = 0; char[] match = null;
            for (Scan scan : scans) {
                checked += scan.checked;
                if (scan.match != null) {
                    if (match != null) { throw new IllegalStateException("Verificação ambígua."); }
                    match = scan.match;
                }
            }
            return new Verification(match, checked);
        } finally { scans.forEach(Scan::close); }
    }

    private Scan scan(String stored, List<DigitPair> pairs, int worker) {
        char[] candidate = new char[pairs.size()];
        char[] match = null; int checked = 0;
        try {
            for (int mask = worker; mask < (1 << pairs.size()); mask += workers) {
                for (int i = 0; i < pairs.size(); i++) { candidate[i] = pairs.get(i).at((mask >>> i) & 1); }
                if (PersonalPasswordPolicy.valid(candidate)) {
                    checked++;
                    if (new PasswordHasher().verify(stored, candidate)) {
                        if (match != null) { Arrays.fill(match, '\0'); throw new IllegalStateException("Verificação ambígua."); }
                        match = candidate.clone();
                    }
                }
            }
            return new Scan(match, checked);
        } catch (RuntimeException e) { if (match != null) { Arrays.fill(match, '\0'); } throw e; }
        finally { Arrays.fill(candidate, '\0'); }
    }
    private static final class Scan implements AutoCloseable {
        final char[] match; final int checked;
        Scan(char[] match, int checked) { this.match = match; this.checked = checked; }
        public void close() { if (match != null) { Arrays.fill(match, '\0'); } }
    }
    public static final class Verification implements AutoCloseable {
        private char[] password;
        private final int checked;
        private boolean closed;
        private Verification(char[] match, int checked) { password = match == null ? null : match.clone(); this.checked = checked; }
        public boolean matched() { return !closed && password != null; }
        public int candidatesChecked() { return checked; }
        char[] passwordCopy() { if (!matched()) { throw new IllegalStateException("Senha indisponível."); } return password.clone(); }
        @Override public void close() { if (password != null) { Arrays.fill(password, '\0'); password = null; } closed = true; }
        @Override public String toString() { return "Verification[redacted]"; }
    }
    @Override public void close() { pool.shutdown(); }
}
