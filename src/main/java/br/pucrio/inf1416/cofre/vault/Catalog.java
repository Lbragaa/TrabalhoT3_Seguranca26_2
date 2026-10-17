package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.db.Group;
import java.util.List;

/** Metadata is created only after both exact-byte catalog signatures have been checked. */
public record Catalog(List<Entry> entries, List<Share> shares) {
    public Catalog { entries = List.copyOf(entries); shares = List.copyOf(shares); }
    public record Entry(String code, String filename, String owner, Group group) { }
    public record Share(String code, long id, String recipient) { }
}
