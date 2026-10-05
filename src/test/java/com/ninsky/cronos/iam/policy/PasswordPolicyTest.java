package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.domain.model.auth.PasswordHistory;
import com.ninsky.cronos.domain.port.auth.PasswordHistoryRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordPolicyTest {

    private static final CommonPasswords COMMON = new CommonPasswords(Set.of("password", "qwerty", "panaderia"));
    private static final SecurityPolicy POLICY = PolicyFixtures.policy();

    private static List<String> check(String password) {
        return PasswordRules.check(password, "jperez", "juan.perez@cronos.mx", POLICY, COMMON);
    }

    @Test
    void acceptsAStrongPassword() {
        assertThat(check("Tr1go-Molido-77")).isEmpty();
    }

    @Test
    void missingPasswordReportsOnlyRequired() {
        assertThat(check(null)).containsExactly(PasswordRules.REQUIRED);
        assertThat(check("")).containsExactly(PasswordRules.REQUIRED);
    }

    @Test
    void reportsEveryBrokenComplexityRule() {
        assertThat(check("abc")).containsExactly(PasswordRules.MIN_LENGTH, PasswordRules.UPPERCASE, PasswordRules.DIGIT,
                PasswordRules.SYMBOL);
        assertThat(check("ABCDEFGHIJKL1!")).containsExactly(PasswordRules.LOWERCASE);
    }

    @Test
    void complexityFlagsFollowThePolicy() {
        SecurityPolicy relaxed = new SecurityPolicy(8, false, false, false, false, 0, 0, 5, 15, 30, 12, 3, 72, List.of(),
                null, null, 0);
        assertThat(PasswordRules.check("onlyletters", null, null, relaxed, COMMON)).isEmpty();
    }

    @Test
    void rejectsMoreThanSeventyTwoUtf8Bytes() {
        String longOne = "Aa1!" + "ñ".repeat(35);
        assertThat(check(longOne)).contains(PasswordRules.MAX_BYTES);
        assertThat(PasswordRules.args(PasswordRules.MAX_BYTES, POLICY)).containsExactly(72);
    }

    @Test
    void rejectsUsernameAndEmailLocalPartCaseInsensitively() {
        assertThat(check("Xx-JPEREZ-2026!")).contains(PasswordRules.CONTAINS_USERNAME);
        assertThat(check("Juan.Perez#2026x")).contains(PasswordRules.CONTAINS_EMAIL);
        assertThat(PasswordRules.check("Ab1!xyzxyzxyz", "ab", "ab@x.mx", POLICY, COMMON)).isEmpty();
    }

    @Test
    void rejectsCommonPasswordsWithPaddingAndLeetspeak() {
        assertThat(COMMON.matches("Password")).isTrue();
        assertThat(COMMON.matches("P@ssw0rd!2024")).isTrue();
        assertThat(COMMON.matches("2024Qwerty!!")).isTrue();
        assertThat(COMMON.matches("P4n4d3r1a#1")).isTrue();
        assertThat(COMMON.matches("Harina-de-Trigo")).isFalse();
        assertThat(check("P@ssw0rd!2024")).contains(PasswordRules.COMMON);
    }

    @Test
    void bundledListLoads() {
        CommonPasswords bundled = CommonPasswords.load();
        assertThat(bundled.size()).isGreaterThan(100);
        assertThat(bundled.matches("123456")).isTrue();
    }

    @Test
    void argumentsCarryPolicyValues() {
        assertThat(PasswordRules.args(PasswordRules.MIN_LENGTH, POLICY)).containsExactly(12);
        assertThat(PasswordRules.args(PasswordRules.REUSED, POLICY)).containsExactly(5);
        assertThat(PasswordRules.args(PasswordRules.UPPERCASE, POLICY)).isEmpty();
    }

    @Test
    void historyIsCheckedOnlyWhenTheCheapRulesPass() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        PasswordHistoryRepositoryPort history = mock(PasswordHistoryRepositoryPort.class);
        UUID userId = UUID.randomUUID();
        when(history.findByUserIdOrderByChangedAtDesc(eq(userId), any())).thenReturn(List.of(
                PasswordHistory.builder().passwordHash(encoder.encode("Tr1go-Molido-77")).build()));
        DefaultPasswordPolicy passwords = new DefaultPasswordPolicy(() -> POLICY, history, encoder);

        assertThat(passwords.violations("Tr1go-Molido-77", "jperez", "j@x.mx", userId)).containsExactly(PasswordRules.REUSED);
        assertThat(passwords.violations("Centeno-Fresco-88", "jperez", "j@x.mx", userId)).isEmpty();

        PasswordHistoryRepositoryPort untouched = mock(PasswordHistoryRepositoryPort.class);
        new DefaultPasswordPolicy(() -> POLICY, untouched, encoder).violations("short", "jperez", "j@x.mx", userId);
        verify(untouched, never()).findByUserIdOrderByChangedAtDesc(any(), any());
    }

    @Test
    void temporaryPasswordsSatisfyThePolicy() {
        DefaultPasswordPolicy passwords = new DefaultPasswordPolicy(() -> POLICY, mock(PasswordHistoryRepositoryPort.class),
                new BCryptPasswordEncoder(4));
        IntStream.range(0, 50).mapToObj(i -> passwords.generateTemporary()).forEach(temporary -> {
            assertThat(temporary).hasSize(14);
            assertThat(PasswordRules.check(temporary, null, null, POLICY, CommonPasswords.load())).isEmpty();
        });
    }
}
