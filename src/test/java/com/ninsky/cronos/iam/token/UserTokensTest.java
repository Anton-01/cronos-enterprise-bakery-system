package com.ninsky.cronos.iam.token;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final UUID USER = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final UserTokenCustomRepository tokens = new UserTokenCustomRepository(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rawTokensAreUrlSafeUniqueAndWellFormed() {
        Set<String> generated = IntStream.range(0, 200).mapToObj(i -> TokenCodec.generate()).collect(Collectors.toSet());
        assertThat(generated).hasSize(200).allSatisfy(raw -> {
            assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(TokenCodec.wellFormed(raw)).isTrue();
        });
        assertThat(TokenCodec.wellFormed(null)).isFalse();
        assertThat(TokenCodec.wellFormed("short")).isFalse();
        assertThat(TokenCodec.wellFormed("a".repeat(42) + "=")).isFalse();
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(TokenCodec.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void issueStoresOnlyTheHashAndInvalidatesEarlierTokens() {
        IssuedToken issued = tokens.issue(USER, TokenPurpose.INVITATION, Duration.ofHours(72));

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        verify(jdbc).update(contains("UPDATE user_tokens SET used_at"), any(SqlParameterSource.class));
        ArgumentCaptor<MapSqlParameterSource> insert = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO user_tokens"), insert.capture());
        assertThat(insert.getValue().getValue("hash")).isEqualTo(TokenCodec.hash(issued.rawToken()));
        assertThat(insert.getValue().getValues().values()).doesNotContain(issued.rawToken());
        assertThat(insert.getValue().getValue("expiresAt")).isEqualTo(Timestamp.from(issued.expiresAt()));
    }

    @Test
    void consumeIsOneConditionalUpdateMatchedByHash() {
        String raw = TokenCodec.generate();
        when(jdbc.queryForList(contains("UPDATE user_tokens SET used_at = :now"), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(USER));

        assertThat(tokens.consume(raw, TokenPurpose.PASSWORD_RESET)).contains(USER);

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), params.capture(), eq(UUID.class));
        assertThat(sql.getValue()).contains("used_at IS NULL", "expires_at > :now", "RETURNING user_id");
        assertThat(params.getValue().getValue("hash")).isEqualTo(TokenCodec.hash(raw));
        assertThat(params.getValue().getValue("purpose")).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void usedOrUnknownTokensYieldNothing() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class))).thenReturn(List.of());
        assertThat(tokens.consume(TokenCodec.generate(), TokenPurpose.INVITATION)).isEqualTo(Optional.empty());
    }

    @Test
    void malformedTokensNeverReachTheDatabase() {
        assertThat(tokens.consume("not-a-token", TokenPurpose.INVITATION)).isEmpty();
        assertThat(tokens.consume(null, TokenPurpose.INVITATION)).isEmpty();
        verifyNoInteractions(jdbc);
    }
}
