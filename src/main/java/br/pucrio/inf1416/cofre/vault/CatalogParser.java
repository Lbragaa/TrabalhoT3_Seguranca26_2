package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.db.Group;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static br.pucrio.inf1416.cofre.vault.VaultException.Reason.CATALOG;

/** Strict ASCII records; punctuation in email identities and filename extensions is supported. */
public final class CatalogParser {
    public Catalog parse(byte[] index, byte[] sharing) throws VaultException {
        try {
            List<Catalog.Entry> entries = new ArrayList<>(); List<Catalog.Share> shares = new ArrayList<>();
            Set<String> codes = new HashSet<>(), names = new HashSet<>(), shareIds = new HashSet<>(), recipients = new HashSet<>();
            Map<String, Catalog.Entry> byCode = new HashMap<>();
            for (String line : lines(index)) {
                String[] fields = fields(line, 4); code(fields[0]); identity(fields[2]);
                if (!SafePaths.safeName(fields[1]) || !codes.add(fields[0].toLowerCase(Locale.ROOT))
                        || !names.add(fields[1].toLowerCase(Locale.ROOT))) { throw new IllegalArgumentException(); }
                var entry = new Catalog.Entry(fields[0], fields[1], fields[2], Group.fromCatalogLabel(fields[3]));
                entries.add(entry); byCode.put(entry.code(), entry);
            }
            for (String line : lines(sharing)) {
                String[] fields = fields(line, 3); code(fields[0]); identity(fields[2]);
                if (!fields[1].matches("[1-9][0-9]*")) { throw new IllegalArgumentException(); }
                long id = Long.parseLong(fields[1]);
                var entry = byCode.get(fields[0]);
                if (entry == null || entry.owner().equals(fields[2]) || !shareIds.add(fields[0]+":"+id)
                        || !recipients.add(fields[0]+":"+fields[2])) { throw new IllegalArgumentException(); }
                shares.add(new Catalog.Share(fields[0], id, fields[2]));
            }
            return new Catalog(entries, shares);
        } catch (IllegalArgumentException e) { throw new VaultException(CATALOG, "Catálogos malformados, duplicados ou com referências inconsistentes."); }
    }
    private List<String> lines(byte[] bytes) {
        for (byte value : bytes) { if (value < 0 || (value < 32 && value != 10 && value != 13)) { throw new IllegalArgumentException(); } }
        String text = new String(bytes, StandardCharsets.US_ASCII);
        if (text.isEmpty()) { return List.of(); }
        String[] lines = text.split("\\r?\\n", -1);
        int count = lines.length - (lines[lines.length-1].isEmpty() ? 1 : 0);
        return Arrays.asList(lines).subList(0, count);
    }
    private String[] fields(String line, int expected) {
        if (line.isEmpty() || !line.equals(line.strip())) { throw new IllegalArgumentException(); }
        String[] values = line.split(" +");
        if (values.length != expected) { throw new IllegalArgumentException(); }
        return values;
    }
    private void code(String value) {
        if (!value.matches("[A-Za-z0-9]{1,240}") || value.equalsIgnoreCase("index") || value.equalsIgnoreCase("share")) { throw new IllegalArgumentException(); }
    }
    private void identity(String value) { if (value.length() > 255 || !value.matches("[^\\s@]+@[^\\s@]+")) { throw new IllegalArgumentException(); } }
}
