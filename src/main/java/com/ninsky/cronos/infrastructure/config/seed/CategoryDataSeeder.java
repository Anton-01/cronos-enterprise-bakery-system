package com.ninsky.cronos.infrastructure.config.seed;

import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the default SYSTEM categories. Deliberately a separate runner from {@link DataSeeder}
 * rather than folded into it: {@code DataSeeder} gates on "is the {@code roles} table empty",
 * which only ever fires on a completely fresh database — that gate is wrong for this data (an
 * environment could have users/roles already but no categories yet, or a future deploy could add
 * new default categories to the list below). Checked per-name instead, so re-running this on an
 * already-seeded database only inserts whatever's missing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryDataSeeder implements CommandLineRunner {

    private static final List<String> PRODUCT_CATEGORIES = List.of(
            "Pasteles (tortas)", "Cupcakes", "Galletas", "Brownies y bars", "Tartas y pays",
            "Cheesecakes", "Mousses", "Flanes y natillas", "Panadería dulce", "Masas laminadas",
            "Chocolatería", "Merengues", "Postres fríos", "Confitería y caramelos", "Decoración y cobertura"
    );

    private static final List<String> INGREDIENT_CATEGORIES = List.of(
            "Flours & Starches", "Sugars & Sweeteners", "Dairy & Eggs", "Fats & Oils",
            "Leavening Agents", "Chocolates & Cocoas", "Fruits & Nuts", "Flavorings & Extracts",
            "Spices", "Additives & Colorings", "Decor & Toppings"
    );

    private final CategoryRepositoryPort categoryRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info(":: CRONOS :: Verifying default SYSTEM categories ...");
        int created = seedType(CategoryType.PRODUCT, PRODUCT_CATEGORIES) + seedType(CategoryType.INGREDIENT, INGREDIENT_CATEGORIES);
        if (created > 0) {
            log.info(":: CRONOS :: Seeded {} default SYSTEM categories.", created);
        } else {
            log.info(":: CRONOS :: Default SYSTEM categories already present, nothing to seed.");
        }
    }

    private int seedType(CategoryType type, List<String> names) {
        int created = 0;
        for (String name : names) {
            if (categoryRepository.existsByNameForOwner(name, type, null)) {
                continue;
            }
            categoryRepository.save(Category.builder().name(name).type(type).scope(CategoryScope.SYSTEM).build());
            created++;
        }
        return created;
    }
}
