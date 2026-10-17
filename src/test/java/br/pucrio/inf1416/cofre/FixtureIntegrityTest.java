package br.pucrio.inf1416.cofre;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixtureIntegrityTest {
    @Test void professorsOriginalPackageMatchesRecordedSha256AndInventory() throws Exception {
        Path root = Path.of(System.getProperty("project.root")).toAbsolutePath().normalize();
        Set<String> expected = new HashSet<>();
        try (var input = getClass().getResourceAsStream("/fixtures.sha256")) {
            assertNotNull(input);
            String manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : manifest.lines().toList()) {
                String[] parts = line.split("  ", 2);
                assertEquals(2, parts.length);
                assertTrue(expected.add(parts[1]), "Duplicate manifest path");
                Path file = root.resolve(parts[1]).normalize();
                assertTrue(file.startsWith(root.resolve("Pacote-T3")));
                assertTrue(Files.isRegularFile(file), "Missing fixture: " + parts[1]);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                assertEquals(parts[0], hash, "Professor fixture changed: " + parts[1]);
            }
        }
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(root.resolve("Pacote-T3"))) {
            files.filter(Files::isRegularFile).forEach(p -> actual.add(root.relativize(p).toString().replace('\\', '/')));
        }
        assertEquals(22, expected.size());
        assertEquals(expected, actual, "Fixture inventory changed");
    }
}
