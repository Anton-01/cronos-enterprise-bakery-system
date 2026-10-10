package eventfixtures;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.util.UUID;

/** Events of the outbox integration test (outside the scanned packages, registered explicitly). */
public final class OrderFixtures {

    private OrderFixtures() {
    }

    @EventType("fixture.order-placed")
    public record OrderPlaced(UUID orderId, int sequence, @Sensitive String secret) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "ORDER";
        }

        @Override
        public UUID aggregateId() {
            return orderId;
        }
    }

    @EventType("fixture.order-shipped")
    public record OrderShipped(UUID orderId) implements DomainEvent {
        @Override
        public String aggregateType() {
            return "SHIPMENT";
        }

        @Override
        public UUID aggregateId() {
            return orderId;
        }
    }
}
