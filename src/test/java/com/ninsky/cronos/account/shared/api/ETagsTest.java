package com.ninsky.cronos.account.shared.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ETagsTest {

    @Test
    void formatsStrongETagFromVersion() {
        assertThat(ETags.of(7)).isEqualTo("\"7\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"*", "  "})
    void absentOrWildcardIfMatchIsNoPrecondition(String ifMatch) {
        assertThat(ETags.parseIfMatch(ifMatch).matches(3L)).isTrue();
        assertThat(ETags.parseIfMatch(ifMatch).matches(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"3\"", "W/\"3\"", "3"})
    void matchesOnlyTheNamedVersion(String ifMatch) {
        assertThat(ETags.parseIfMatch(ifMatch).matches(3L)).isTrue();
        assertThat(ETags.parseIfMatch(ifMatch).matches(4L)).isFalse();
        assertThat(ETags.parseIfMatch(ifMatch).matches(null)).isFalse();
    }

    @Test
    void garbageNeverMatches() {
        assertThat(ETags.parseIfMatch("\"abc\"").matches(3L)).isFalse();
    }
}
