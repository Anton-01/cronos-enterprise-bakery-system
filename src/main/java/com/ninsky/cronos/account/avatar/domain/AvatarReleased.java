package com.ninsky.cronos.account.avatar.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.util.UUID;

/** An avatar object is no longer referenced by its user and can be deleted from storage (outbox, after commit). */
@EventType("account.avatar-released")
public record AvatarReleased(UUID userId, String key) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "USER";
    }

    @Override
    public UUID aggregateId() {
        return userId;
    }
}
