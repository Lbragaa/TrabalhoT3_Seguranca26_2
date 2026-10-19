/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.crypto.PasswordHasher;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class KeypadPasswordVerifierTest {
    @ParameterizedTest @ValueSource(ints = {8,9,10})
    void fullWorstCaseScanFindsPasswordAndChecksEveryCandidate(int length) throws Exception {
        char[] password = new char[length]; List<DigitPair> pairs = new ArrayList<>();
        for (int i = 0; i < length; i++) { char first = (char) ('0' + 2 * (i % 5)); char second = (char) (first + 1); pairs.add(new DigitPair(first,second)); password[i] = second; }
        try (var verifier = new KeypadPasswordVerifier()) {
            try (var result = verifier.verify(new PasswordHasher().hash(password), pairs)) {
                assertTrue(result.matched()); assertEquals(1 << length, result.candidatesChecked());
                char[] recovered = result.passwordCopy(); try { assertTrue(Arrays.equals(password,recovered)); } finally { Arrays.fill(recovered,'\0'); }
            }
        } finally { Arrays.fill(password,'\0'); }
    }
    @Test void wrongPasswordAndPolicyPruningStillPerformCompleteEligibleScan() throws Exception {
        var pair = new DigitPair('0','1'); var captured = Collections.nCopies(8,pair); char[] other = RegistrationTestSupport.password();
        // Force a non-member digit while retaining a valid disposable personal password.
        other[0]='9'; other[1]='8';
        try (var verifier = new KeypadPasswordVerifier(); var result = verifier.verify(new PasswordHasher().hash(other),captured)) {
            assertFalse(result.matched()); assertEquals(2,result.candidatesChecked()); assertThrows(IllegalStateException.class,result::passwordCopy);
        } finally { Arrays.fill(other,'\0'); }
    }
    @Test void earlyMatchingCandidateDoesNotShortCircuitAndClosedResultCannotReleasePassword() throws Exception {
        char[] password = new char[8];for(int i=0;i<8;i++){password[i]=(char)('0'+i%2);}
        try(var verifier=new KeypadPasswordVerifier()){
            var result=verifier.verify(new PasswordHasher().hash(password),Collections.nCopies(8,new DigitPair('0','1')));
            assertTrue(result.matched());assertEquals(2,result.candidatesChecked());result.close();assertFalse(result.matched());assertThrows(IllegalStateException.class,result::passwordCopy);
            assertThrows(IllegalArgumentException.class,()->verifier.verify("invalid",List.of()));
        }finally{Arrays.fill(password,'\0');}
    }
}
