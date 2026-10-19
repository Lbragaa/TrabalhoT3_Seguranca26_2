/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre;

import br.pucrio.inf1416.cofre.audit.AuditEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {
    @Test void catalogHasExactlyTheSpecifiedCodes() {
        int[][] ranges = {{1001,1006},{2001,2005},{3001,3007},{4001,4007},
                {5001,5004},{6001,6010},{7001,7021},{8001,8004}};
        Set<Integer> expected = new HashSet<>();
        for (int[] range : ranges) {
            for (int code = range[0]; code <= range[1]; code++) { expected.add(code); }
        }
        var actual = Arrays.stream(AuditEvent.values()).map(AuditEvent::code).toList();
        assertEquals(64, actual.size());
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(actual.size(), new HashSet<>(actual).size());
        for (AuditEvent event : AuditEvent.values()) {
            assertSame(event, AuditEvent.fromCode(event.code()));
            assertFalse(event.template().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> AuditEvent.fromCode(9999));
    }

    @Test void templateContextsRemainUnexpanded() {
        assertEquals("Sistema iniciado.", AuditEvent.E1001.template());
        assertEquals("Arquivo <arq_name> selecionado por <login_name> para decriptação.", AuditEvent.E7015.template());
        assertEquals("Botão voltar de sair para o menu principal pressionado por <login_name>.", AuditEvent.E8004.template());
    }
}
