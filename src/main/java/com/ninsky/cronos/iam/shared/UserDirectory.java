package com.ninsky.cronos.iam.shared;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves {@link UserRef}s in bulk (one query for any number of ids). */
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final UserDirectoryCustomRepository repository;
    private final AvatarStorage avatarStorage;

    public Map<UUID, UserRef> refs(Collection<UUID> ids) {
        var distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return repository.find(distinct).stream()
                .map(r -> new UserRef(r.id(), r.username(), UserRef.displayName(r.firstName(), r.lastName(), r.username()),
                        avatarUrl(r.avatarKey())))
                .collect(Collectors.toMap(UserRef::id, Function.identity()));
    }

    public Optional<UserRef> ref(UUID id) {
        return Optional.ofNullable(id).map(i -> refs(List.of(i)).get(i));
    }

    public String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : avatarStorage.publicUrl(AvatarKey.ofNullable(avatarKey)).toString();
    }
}
