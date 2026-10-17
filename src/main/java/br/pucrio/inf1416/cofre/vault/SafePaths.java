package br.pucrio.inf1416.cofre.vault;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import static br.pucrio.inf1416.cofre.vault.VaultException.Reason.*;

/** Portable Windows-safe basenames and canonical filesystem boundaries. */
public final class SafePaths {
    private SafePaths() { }
    public static boolean safeName(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.endsWith(".") || value.endsWith(" ")) { return false; }
        if (value.equals(".") || value.equals("..") || value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")) { return false; }
        String device = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return !device.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]") && !Path.of(value).isAbsolute();
    }
    public static Path directory(String value) throws VaultException {
        try {
            if (value == null || value.isBlank() || value.length() > 255) { throw new IOException(); }
            Path real = Path.of(value).toRealPath();
            if (!Files.isDirectory(real) || !Files.isReadable(real)) { throw new IOException(); }
            return real;
        } catch (IOException | InvalidPathException e) { throw new VaultException(INVALID_PATH, "Caminho de pasta inválido ou inacessível."); }
    }
    public static Path input(Path directory, String name) throws IOException {
        Path file = directory.resolve(name);
        if (!safeName(name) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || !file.toRealPath().getParent().equals(directory.toRealPath())) { throw new NoSuchFileException("Arquivo protegido ausente ou caminho inseguro."); }
        return file;
    }
    public static Path output(Path directory, String filename, Path vault) throws VaultException {
        if (directory == null || !safeName(filename)) { throw new VaultException(INVALID_PATH, "Nome secreto ou destino inválido."); }
        try {
            Path real = directory.toRealPath();
            Path fixtures = Path.of("Pacote-T3").toAbsolutePath().normalize();
            if (Files.exists(fixtures)) { fixtures = fixtures.toRealPath(); }
            if (!Files.isDirectory(real) || !Files.isWritable(real) || real.startsWith(fixtures) || real.startsWith(vault.toRealPath())) {
                throw new VaultException(INVALID_PATH, "Escolha uma pasta de saída fora da pasta segura e de Pacote-T3.");
            }
            Path target = real.resolve(filename);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { throw new VaultException(EXISTS, "Já existe um arquivo com esse nome. Nada foi sobrescrito."); }
            return target;
        } catch (IOException e) { throw new VaultException(INVALID_PATH, "Pasta de saída inválida ou inacessível."); }
    }
}
