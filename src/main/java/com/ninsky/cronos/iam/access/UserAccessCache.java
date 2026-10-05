package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Effective access keyed by (userId, accessVersion): a bump makes old entries unreachable. */
@Component
@RequiredArgsConstructor
public class UserAccessCache {

    private final AccessSnapshotLoader loader;

    @Cacheable(value = AccessCaches.EFFECTIVE_ACCESS, key = "#userId + ':' + #accessVersion")
    public UserAccessState load(UUID userId, long accessVersion) {
        AccessSnapshot snapshot = loader.load(userId);
        return new UserAccessState(accessVersion,
                snapshot.roles().stream().filter(RoleGrant::active).map(RoleGrant::code).sorted().toList(),
                EffectivePermissionResolver.resolve(snapshot));
    }
}
