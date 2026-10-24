package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code GET /recipes} list query against a real PostgreSQL running every migration: the page CTE is joined back to
 * {@code recipes}, rows come back in page order with category name, cover key and the window total, and a page past
 * the end still reports the total.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RecipeQueryCustomRepository.class, RecipeCustomRepository.class})
class RecipeListPagePostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID TENANT = UUID.randomUUID();
    private static final RecipeFilter ALL = new RecipeFilter(null, null, null, null, null, null);

    @MockitoBean
    private FieldEncryptionService fieldEncryption;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RecipeQueryCustomRepository queries;

    @Test
    void listPageJoinsThePageBackAndKeepsItsOrder() {
        UUID newest = jdbc.queryForObject("SELECT id FROM recipes WHERE deleted_at IS NULL AND category_id IS NOT NULL ORDER BY id LIMIT 1",
                UUID.class);
        jdbc.update("UPDATE recipes SET updated_at = ? WHERE id = ?", Timestamp.from(Instant.now().plusSeconds(3600)), newest);
        jdbc.update("""
                INSERT INTO recipe_files (id, recipe_id, storage_key, file_name, kind, mime_type, size_bytes, is_cover, card_storage_key)
                VALUES (?, ?, 'recipes/cover.jpg', 'cover.jpg', 'IMAGE', 'image/jpeg', 10, TRUE, 'recipes/cover-card.jpg')""",
                UUID.randomUUID(), newest);
        long total = jdbc.queryForObject("SELECT count(*) FROM recipes WHERE deleted_at IS NULL AND owner_id IS NULL", Long.class);
        List<UUID> expected = jdbc.queryForList("""
                SELECT id FROM recipes WHERE deleted_at IS NULL AND owner_id IS NULL
                ORDER BY coalesce(updated_at, created_at) DESC NULLS LAST, id LIMIT 3""", UUID.class);
        String categoryName = jdbc.queryForObject("SELECT c.name FROM recipes r JOIN categories c ON c.id = r.category_id WHERE r.id = ?",
                String.class, newest);
        assertThat(total).isGreaterThan(3);

        CatalogPage<RecipeCustomRepository.ListRow> page = queries.page(TENANT, ALL, query(0, 3));

        assertThat(page.totalElements()).isEqualTo(total);
        assertThat(page.content()).extracting(row -> row.head().id()).containsExactlyElementsOf(expected);
        RecipeCustomRepository.ListRow first = page.content().getFirst();
        assertThat(first.head().id()).isEqualTo(newest);
        assertThat(first.categoryName()).isEqualTo(categoryName);
        assertThat(first.coverKey()).isEqualTo("recipes/cover-card.jpg");
        assertThat(page.content().get(1).coverKey()).isNull();
    }

    @Test
    void pastTheEndStillReportsTheTotal() {
        long total = jdbc.queryForObject("SELECT count(*) FROM recipes WHERE deleted_at IS NULL AND owner_id IS NULL", Long.class);

        CatalogPage<RecipeCustomRepository.ListRow> page = queries.page(TENANT, ALL, query(1000, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(total);
    }

    private static PageQuery query(int page, int size) {
        return PageQuery.of(page, size, null, RecipeQueryCustomRepository.SORTS, RecipeQueryCustomRepository.DEFAULT_SORT);
    }
}
