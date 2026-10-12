package com.ninsky.cronos.kitchen.ingredient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /ingredients/{id}/prices} against a real PostgreSQL running every migration: the payload nests the
 * purchase under {@code price} and always carries {@code changePercent}, computed per series (own vs reference).
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({IngredientQueryCustomRepository.class, IngredientCustomRepository.class, UserRefCustomRepository.class})
class IngredientPriceHistoryPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-05T18:00:00Z");

    @MockitoBean
    private RequestContextUtil requestContext;
    @MockitoBean
    private AvatarStorage avatarStorage;
    @MockitoBean
    private FieldEncryptionService fieldEncryption;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IngredientQueryCustomRepository queries;
    @Autowired
    private IngredientCustomRepository repository;

    @Test
    void priceHistoryNestsThePurchaseAndAlwaysCarriesChangePercent() throws Exception {
        insertTenantUser();
        Map<String, Object> reference = jdbc.queryForMap("""
                SELECT p.ingredient_id, p.purchase_unit_id, mu.code_identity FROM ingredient_prices p
                JOIN measurement_units mu ON mu.id = p.purchase_unit_id WHERE p.owner_id IS NULL ORDER BY p.ingredient_id LIMIT 1""");
        UUID ingredientId = (UUID) reference.get("ingredient_id");
        long unitId = ((Number) reference.get("purchase_unit_id")).longValue();
        String unitCode = (String) reference.get("code_identity");

        repository.insertPrice(new IngredientCustomRepository.NewPrice(UUID.randomUUID(), ingredientId, TENANT, new BigDecimal("1.0000"),
                unitId, new BigDecimal("200.0000"), "MXN", "Proveedor A", LocalDate.of(2026, 10, 2), new BigDecimal("0.20000000")),
                TENANT, NOW);
        repository.insertPrice(new IngredientCustomRepository.NewPrice(UUID.randomUUID(), ingredientId, TENANT, new BigDecimal("1.0000"),
                unitId, new BigDecimal("224.2400"), "MXN", "Proveedor B", LocalDate.of(2026, 10, 3), new BigDecimal("0.22424000")),
                TENANT, NOW.plusSeconds(60));

        mvc().perform(get("/ingredients/{id}/prices", ingredientId).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].price.pricedAt").value("2026-10-03"))
                .andExpect(jsonPath("$.data.content[0].price.purchaseUnitCode").value(unitCode))
                .andExpect(jsonPath("$.data.content[0].price.purchaseUnitId").value(unitId))
                .andExpect(jsonPath("$.data.content[0].price.price").value(closeTo(224.24, 0.0001)))
                .andExpect(jsonPath("$.data.content[0].price.currency").value("MXN"))
                .andExpect(jsonPath("$.data.content[0].price.supplier").value("Proveedor B"))
                .andExpect(jsonPath("$.data.content[0]", not(hasKey("pricedAt"))))
                .andExpect(jsonPath("$.data.content[0].costPerBaseUnit").value(closeTo(0.22424, 0.0000001)))
                .andExpect(jsonPath("$.data.content[0].changePercent").value(closeTo(12.12, 0.0001)))
                .andExpect(jsonPath("$.data.content[0].recordedBy.id").value(TENANT.toString()))
                .andExpect(jsonPath("$.data.content[1].price.pricedAt").value("2026-10-02"))
                .andExpect(jsonPath("$.data.content[1]", hasKey("changePercent")))
                .andExpect(jsonPath("$.data.content[1].changePercent").value(nullValue()))
                // The seeded reference price is its own series: oldest there, so null too; seed rows have no recorder.
                .andExpect(jsonPath("$.data.content[2].price.supplier").value("Referencia Cronos"))
                .andExpect(jsonPath("$.data.content[2]", hasKey("changePercent")))
                .andExpect(jsonPath("$.data.content[2].changePercent").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].recordedBy").value(nullValue()));
    }

    private MockMvc mvc() {
        ActorProvider actors = mock(ActorProvider.class);
        Actor actor = new Actor(TENANT, "baker", Set.of("INGREDIENT.INGREDIENT.READ"), false);
        when(actors.require()).thenReturn(actor);
        when(actors.current()).thenReturn(Optional.of(actor));
        IngredientService service = new IngredientService(queries, repository, mock(IngredientValidator.class), mock(IngredientViews.class),
                mock(IngredientRipple.class), mock(AllergenCatalog.class), mock(KitchenCaches.class), mock(KitchenMessages.class),
                mock(AuditRecorder.class), actors, Clock.fixed(NOW, ZoneOffset.UTC));
        return MockMvcBuilders.standaloneSetup(new IngredientController(service, mock(KitchenMessages.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private void insertTenantUser() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("""
                INSERT INTO users (id, username, email, email_blind_index, enabled, account_non_locked, account_non_expired,
                                   credentials_non_expired, email_verified, two_factor_enabled, failed_login_attempts,
                                   password_needs_change, created_at, version)
                VALUES (?, 'baker', 'baker@cronos.test', ?, true, true, true, true, false, false, 0, false, ?, 0)""",
                TENANT, TENANT.toString().substring(0, 32), now);
        jdbc.update("""
                INSERT INTO user_profiles (id, user_id, first_name, last_name, email_notifications, push_notifications,
                                           sms_notifications, created_at)
                VALUES (?, ?, 'Antón', 'Admin', true, true, false, ?)""", UUID.randomUUID(), TENANT, now);
    }
}
