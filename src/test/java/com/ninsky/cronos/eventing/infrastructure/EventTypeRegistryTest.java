package com.ninsky.cronos.eventing.infrastructure;

import com.ninsky.cronos.eventing.domain.RetrySchedule;
import eventfixtures.RegistryFixtures.Malformed;
import eventfixtures.RegistryFixtures.SameName;
import eventfixtures.RegistryFixtures.ThingHappened;
import eventfixtures.RegistryFixtures.Unannotated;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTypeRegistryTest {

    @Test
    void registersTypeRetryAndSensitiveComponents() {
        EventTypeRegistry registry = EventTypeRegistry.of(List.of(ThingHappened.class));
        EventTypeRegistry.Registration registration = registry.registrationOf(new ThingHappened(UUID.randomUUID(), "s", "n"));
        assertThat(registration.type()).isEqualTo("fixture.thing-happened");
        assertThat(registration.retry()).isEqualTo(RetrySchedule.MAIL);
        assertThat(registration.sensitiveComponents()).containsExactly("secret");
        assertThat(registry.byType("fixture.thing-happened")).contains(registration);
        assertThat(registry.byType("fixture.unknown")).isEmpty();
    }

    @Test
    void failsFastOnDuplicatesMalformedNamesAndUnannotatedEvents() {
        assertThatThrownBy(() -> EventTypeRegistry.of(List.of(ThingHappened.class, SameName.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fixture.thing-happened");
        assertThatThrownBy(() -> EventTypeRegistry.of(List.of(Malformed.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Malformed");
        EventTypeRegistry registry = EventTypeRegistry.of(List.of(ThingHappened.class));
        assertThatThrownBy(() -> registry.registrationOf(new Unannotated(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theApplicationCatalogScansCleanly() {
        assertThat(new EventTypeRegistry().types()).allMatch(type -> type.matches("^[a-z]+(\\.[a-z][a-z-]*)+$"));
    }
}
