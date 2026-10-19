/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.db;

/** Stable database IDs and exact authenticated catalog labels. */
public enum Group {
    ADMINISTRATOR(1, "administrador", "Administrador"), USER(2, "usuario", "Usuário");
    private final int id;
    private final String catalogLabel;
    private final String displayName;
    Group(int id, String catalogLabel, String displayName) {
        this.id = id; this.catalogLabel = catalogLabel; this.displayName = displayName;
    }
    public int id() { return id; }
    public String catalogLabel() { return catalogLabel; }
    public String displayName() { return displayName; }
    public static Group fromId(int id) {
        for (Group group : values()) { if (group.id == id) { return group; } }
        throw new IllegalArgumentException("Grupo desconhecido.");
    }
    public static Group fromCatalogLabel(String label) {
        for (Group group : values()) { if (group.catalogLabel.equals(label)) { return group; } }
        throw new IllegalArgumentException("Grupo do catálogo desconhecido.");
    }
    @Override public String toString() { return displayName; }
}
