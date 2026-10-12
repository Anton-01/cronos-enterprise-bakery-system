package com.ninsky.cronos.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.infrastructure.web.ServerTimingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * performance-latency §4: the number of SQL statements each endpoint runs, read from its {@code Server-Timing: db;…;desc="n
 * stmts"} entry (datasource-proxy on, {@code app.observability.sql.enabled=true}), against the real PostgreSQL and Redis
 * the build provides (same setup as {@code CronosApplicationTests}), signed in as the seeded {@code admin_cronos}.
 * <p>
 * Budgets: every list endpoint ≤ 3, {@code /users/me} = 1, the stats/summary/unread endpoints = 1, sign-in ≤ 2 reads +
 * 1 write transaction, an unauthenticated 401 = 0. Also checks that the sign-in side effects arrive through the outbox.
 */
@SpringBootTest(properties = "app.observability.sql.enabled=true")
@AutoConfigureMockMvc
class StatementBudgetTest {

    private static final Pattern DB = Pattern.compile("db;dur=[0-9.]+;desc=\"(\\d+) stmts\"");
    private static final String ADMIN = "admin_cronos";
    private static final String PASSWORD = "SuperAdmin2026!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CacheManager caches;
    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        // First sign-in warms the per-instance caches (policies, effective access, menu); the budget below is the warm path.
        token = login(PASSWORD).path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    void anUnauthenticatedRequestRunsNoStatement() throws Exception {
        MvcResult result = mvc.perform(get("/ingredients")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(statements(result)).isZero();
    }

    @Test
    void signInIsOneReadAndOneWriteTransaction() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + ADMIN + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // sign-in row + (session + refresh token) + outbox row; ≤ 2 reads + 1 write transaction allows one more read.
        assertThat(statements(result)).isLessThanOrEqualTo(4);
        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("user").path("username").asText()).isEqualTo(ADMIN);
        assertThat(data.path("refreshToken").asText()).isNotBlank();
    }

    @Test
    void signInSideEffectsArriveThroughTheOutbox() throws Exception {
        Instant before = Instant.now();
        login(PASSWORD);
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, ADMIN);

        UUID eventId = jdbc.queryForObject("""
                SELECT id FROM outbox_messages WHERE type = 'identity.user-signed-in' AND aggregate_id = ?
                ORDER BY occurred_at DESC LIMIT 1""", UUID.class, userId);
        // The login_history row of a sign-in carries the event id (idempotent consumer), and last_login_at moves.
        await(() -> count("SELECT count(*) FROM login_history WHERE id = ?", eventId) == 1);
        await(() -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT last_login_at >= ? FROM users WHERE id = ?", Boolean.class,
                java.sql.Timestamp.from(before.minusSeconds(5)), userId)));
        assertThat(count("SELECT count(*) FROM device_fingerprints WHERE user_id = ?", userId)).isPositive();
    }

    @Test
    void aWrongPasswordIsCountedInRedisAndJournaledThroughTheOutbox() throws Exception {
        MvcResult wrong = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + ADMIN + "\",\"password\":\"Not-the-password-1\"}")).andReturn();

        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(wrong.getResponse().getContentAsString()).toString()).contains("remainingAttempts");
        // Request path: the sign-in read and the sign-in-failed outbox row only.
        assertThat(statements(wrong)).isLessThanOrEqualTo(2);
        login(PASSWORD); // clears the failure window again
    }

    @Test
    void readEndpointsStayWithinTheirBudget() throws Exception {
        // Each list is called once first: a user's first call may seed defaults (fixed costs, sections) or load the
        // per-instance allergen catalog; the budget is the steady state.
        assertThat(warmThenMeasure("/users/me")).isEqualTo(1);
        assertThat(warmThenMeasure("/notifications/unread-count")).isEqualTo(1);
        assertThat(warmThenMeasure("/notifications")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/ingredients")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/recipes")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/quotes")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/user-fixed-cost")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/finance/currencies")).isLessThanOrEqualTo(3);
        assertThat(warmThenMeasure("/finance/tax-rates")).isLessThanOrEqualTo(3);
    }

    @Test
    void aggregatesAreOneStatementAndThenCached() throws Exception {
        statementsOf("/quotes/stats"); // warms the finance settings it reads its currency from
        clear("quoteStats");
        assertThat(statementsOf("/quotes/stats")).isEqualTo(1);
        assertThat(statementsOf("/quotes/stats")).isZero();

        clear("dashboardSummary");
        assertThat(statementsOf("/dashboard/summary")).isEqualTo(1);
        assertThat(statementsOf("/dashboard/summary")).isZero();
    }

    @Test
    void referenceCatalogsAreCachedAndRevalidated() throws Exception {
        clear("categories");
        assertThat(statementsOf("/category?type=INGREDIENT")).isLessThanOrEqualTo(1);
        assertThat(statementsOf("/category?type=INGREDIENT")).isZero();
        assertThat(statementsOf("/measurement-unit/catalog")).isLessThanOrEqualTo(1);
        assertThat(statementsOf("/measurement-unit/catalog")).isZero();
        assertThat(statementsOf("/finance/currencies/catalog")).isLessThanOrEqualTo(1);

        MvcResult first = mvc.perform(get("/finance/currencies/catalog").header("Authorization", "Bearer " + token)).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        assertThat(first.getResponse().getHeader("Cache-Control")).contains("max-age=60");
        MvcResult revalidated = mvc.perform(get("/finance/currencies/catalog").header("Authorization", "Bearer " + token)
                .header("If-None-Match", etag)).andReturn();
        assertThat(revalidated.getResponse().getStatus()).isEqualTo(304);
        assertThat(statements(revalidated)).isZero();
    }

    @Test
    void aCategoryWriteEvictsTheCacheAndChangesTheEtag() throws Exception {
        String path = "/category?type=PRODUCT&size=100";
        String before = mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse().getHeader("ETag");
        String name = "perf-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult created = mvc.perform(post("/category").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"cache test\",\"type\":\"PRODUCT\"}")).andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        MvcResult after = mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();

        assertThat(after.getResponse().getHeader("ETag")).isNotEqualTo(before);
        assertThat(after.getResponse().getContentAsString()).contains(name);
        long id = json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/category/" + id)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void streamTicketsTouchRedisOnly() throws Exception {
        MvcResult result = mvc.perform(post("/notifications/stream/ticket").header("Authorization", "Bearer " + token)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(statements(result)).isZero();
    }

    private JsonNode login(String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + ADMIN + "\",\"password\":\"" + password + "\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int warmThenMeasure(String path) throws Exception {
        statementsOf(path);
        return statementsOf(path);
    }

    private int statementsOf(String path) throws Exception {
        MvcResult result = mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path + " → " + result.getResponse().getContentAsString()).isBetween(200, 299);
        return statements(result);
    }

    private static int statements(MvcResult result) {
        String header = result.getResponse().getHeader(ServerTimingFilter.HEADER);
        assertThat(header).as("Server-Timing").isNotNull();
        Matcher matcher = DB.matcher(header);
        assertThat(matcher.find()).as("db entry in " + header).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private void clear(String cache) {
        Cache named = caches.getCache(cache);
        if (named != null) {
            named.clear();
        }
    }

    private long count(String sql, Object arg) {
        Long value = jdbc.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("condition not met within 15 s").isBefore(deadline);
            Thread.sleep(100);
        }
    }
}
