package eventfixtures;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.util.UUID;

/**
 * Deliberately broken event declarations for registry tests. They live outside {@code com.ninsky.cronos} so the
 * application's classpath scan (and therefore every Spring context of the test suite) never sees them.
 */
public final class RegistryFixtures {

    private RegistryFixtures() {
    }

    @EventType(value = "fixture.thing-happened", retry = RetrySchedule.MAIL)
    public record ThingHappened(UUID id, @Sensitive String secret, String name) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "THING";
        }

        @Override
        public UUID aggregateId() {
            return id;
        }
    }

    @EventType("fixture.thing-happened")
    public record SameName(UUID id) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "THING";
        }

        @Override
        public UUID aggregateId() {
            return id;
        }
    }

    @EventType("Bad Name")
    public record Malformed(UUID id) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "THING";
        }

        @Override
        public UUID aggregateId() {
            return id;
        }
    }

    public record Unannotated(UUID id) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "THING";
        }

        @Override
        public UUID aggregateId() {
            return id;
        }
    }
}
