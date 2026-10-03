package com.ninsky.cronos.account.shared.domain.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * Business audit event published by every account use case and persisted (after commit) to
 * {@code audit_log}. Sealed: the writer's mapping to an {@code AuditAction} is an exhaustive switch.
 * {@code changes} are already PII-masked (see {@link AuditDiff}).
 */
public sealed interface AuditChange permits AuditChange.ProfileChanged, AuditChange.AvatarChanged, AuditChange.AvatarRemoved,
        AuditChange.FiscalDataCreated, AuditChange.FiscalDataUpdated, AuditChange.PasswordChanged {

    UUID actorId();

    UUID resourceId();

    SequencedMap<String, FieldDiff> changes();

    ResourceType resourceType();

    private static SequencedMap<String, FieldDiff> immutable(SequencedMap<String, FieldDiff> changes) {
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(changes == null ? new LinkedHashMap<>() : changes));
    }

    record ProfileChanged(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public ProfileChanged {
            Objects.requireNonNull(actorId);
            changes = immutable(changes);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.USER_PROFILE;
        }
    }

    record AvatarChanged(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public AvatarChanged {
            Objects.requireNonNull(actorId);
            changes = immutable(changes);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.USER_AVATAR;
        }
    }

    record AvatarRemoved(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public AvatarRemoved {
            Objects.requireNonNull(actorId);
            changes = immutable(changes);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.USER_AVATAR;
        }
    }

    record FiscalDataCreated(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public FiscalDataCreated {
            Objects.requireNonNull(actorId);
            changes = immutable(changes);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.FISCAL_DATA;
        }
    }

    record FiscalDataUpdated(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public FiscalDataUpdated {
            Objects.requireNonNull(actorId);
            changes = immutable(changes);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.FISCAL_DATA;
        }
    }

    /** Never carries a diff: password hashes are not audit material. */
    record PasswordChanged(UUID actorId, UUID resourceId, SequencedMap<String, FieldDiff> changes) implements AuditChange {
        public PasswordChanged {
            Objects.requireNonNull(actorId);
            changes = immutable(null);
        }

        public PasswordChanged(UUID actorId) {
            this(actorId, actorId, null);
        }

        @Override
        public ResourceType resourceType() {
            return ResourceType.CREDENTIALS;
        }
    }
}
