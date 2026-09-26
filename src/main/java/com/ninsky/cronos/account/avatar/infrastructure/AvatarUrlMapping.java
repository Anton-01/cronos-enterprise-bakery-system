package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Avatar key → public URL, resolved through the storage port (never assembled in an entity or DTO).
 * Used by MapStruct mappers via {@code uses}.
 */
@Component
@RequiredArgsConstructor
public class AvatarUrlMapping {

    private final AvatarStorage avatarStorage;

    @Named("avatarUrl")
    public String avatarUrl(AvatarKey key) {
        return key == null ? null : avatarStorage.publicUrl(key).toString();
    }
}
