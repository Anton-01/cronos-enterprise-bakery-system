package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.api.SecurityPolicyView;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads and updates the singleton security policy (spec §8). */
@Service
@RequiredArgsConstructor
public class SecurityPolicyService {

    private static final String TARGET_LABEL = "Security policy";

    private final SecurityPolicyCustomRepository store;
    private final UserDirectory users;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SecurityPolicyView get() {
        return view(store.load());
    }

    @Transactional
    public SecurityPolicyView update(SecurityPolicyRequest request) {
        var actor = actors.require();
        SecurityPolicyRules.validate(request, store.activeRoleIds(Optional.ofNullable(request.twoFactorRequiredRoleIds())
                .orElse(List.of()).stream().filter(Objects::nonNull).toList()), store.superAdminRoleId()).throwIfAny();
        SecurityPolicy before = store.load();
        SecurityPolicy after = request.toPolicy(TenantTime.now(clock), actor.id());
        if (before.version() != request.version() || !store.update(after, request.version())) {
            throw ApiException.concurrentModification();
        }
        List<String> weakened = SecurityPolicyRules.weakenedControls(before, after);
        Map<String, Object> previous = SecurityPolicyRules.snapshot(before);
        Map<String, Object> changes = new LinkedHashMap<>();
        SecurityPolicyRules.snapshot(after).forEach((field, value) -> Changes.put(changes, field, previous.get(field), value));
        recorder.record(AuditEvent.of(AuditAction.SECURITY_POLICY_UPDATED, AuditTargets.SECURITY_POLICY, 1, TARGET_LABEL)
                .severity(weakened.isEmpty() ? AuditSeverity.NOTICE : AuditSeverity.WARNING)
                .changes(changes)
                .params(Map.of("weakened", weakened))
                .build());
        events.publishEvent(new SecurityPolicyChanged(after.version()));
        return view(store.load());
    }

    private SecurityPolicyView view(SecurityPolicy policy) {
        return SecurityPolicyView.of(policy, users.ref(policy.updatedBy()).orElse(null));
    }
}
