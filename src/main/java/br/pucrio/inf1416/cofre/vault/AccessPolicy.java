/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.util.*;

public final class AccessPolicy {
    public enum Reason { OWNER, GROUP, SHARE }
    public record Decision(Set<Reason> reasons, Long shareId) {
        public Decision { reasons = Set.copyOf(reasons); }
        public boolean visible() { return !reasons.isEmpty(); }
        public boolean decryptable() { return reasons.contains(Reason.OWNER) || reasons.contains(Reason.SHARE); }
        public String envelope(String code) { return reasons.contains(Reason.OWNER) ? code+".env" : shareId == null ? null : code+"."+shareId+".shr"; }
    }
    public Decision evaluate(User user, Catalog.Entry entry, Catalog catalog) {
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class); Long id = null;
        if (user != null && entry != null && catalog.entries().contains(entry)) {
            if (entry.owner().equals(user.email())) { reasons.add(Reason.OWNER); }
            if (entry.group() == user.group()) { reasons.add(Reason.GROUP); }
            for (var share : catalog.shares()) {
                if (share.code().equals(entry.code()) && share.recipient().equals(user.email())) { reasons.add(Reason.SHARE); id = share.id(); }
            }
        }
        return new Decision(reasons, id);
    }
    public List<Catalog.Entry> listing(User user, Catalog catalog) {
        return catalog.entries().stream().filter(entry -> evaluate(user, entry, catalog).visible()).toList();
    }
}
