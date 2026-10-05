package com.ninsky.cronos.iam.sod;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.ninsky.cronos.iam.permission.Permissions.*;
import static org.assertj.core.api.Assertions.assertThat;

class SodEvaluatorTest {

    private static final SodRule QUOTES = new SodRule("SOD_QUOTE_CREATE_APPROVE", "Crear y aprobar", "Create and approve",
            "desc es", "desc en", SodSeverity.WARNING, List.of(Set.of(QUOTE_CREATE), Set.of(QUOTE_APPROVE)));
    private static final SodRule POLICY = new SodRule("SOD_POLICY_AND_ACCESS", "Política", "Policy", "es", "en", SodSeverity.BLOCKING,
            List.of(Set.of(IAM_SECURITY_POLICY_UPDATE), Set.of(IAM_USER_MANAGE_ACCESS), Set.of(IAM_USER_RESET_CREDENTIALS)));

    @Test
    void conflictNeedsOneCodeFromEverySet() {
        assertThat(SodEvaluator.evaluate(List.of(QUOTES, POLICY), Set.of(QUOTE_CREATE), Locale.ENGLISH)).isEmpty();

        var conflicts = SodEvaluator.evaluate(List.of(QUOTES, POLICY), Set.of(QUOTE_CREATE, QUOTE_APPROVE, IAM_USER_READ), Locale.ENGLISH);

        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.code()).isEqualTo("SOD_QUOTE_CREATE_APPROVE");
            assertThat(c.name()).isEqualTo("Create and approve");
            assertThat(c.blocking()).isFalse();
            assertThat(c.permissions()).containsExactly(QUOTE_APPROVE, QUOTE_CREATE);
        });
    }

    @Test
    void blockingRuleIsReportedInSpanishByDefault() {
        var conflicts = SodEvaluator.evaluate(List.of(POLICY),
                Set.of(IAM_SECURITY_POLICY_UPDATE, IAM_USER_MANAGE_ACCESS, IAM_USER_RESET_CREDENTIALS), Locale.of("es"));
        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.blocking()).isTrue();
            assertThat(c.name()).isEqualTo("Política");
        });
    }
}
