package com.ninsky.cronos.account.profile.infrastructure;

import com.ninsky.cronos.account.avatar.api.AvatarResponse;
import com.ninsky.cronos.account.avatar.infrastructure.AvatarUrlMapping;
import com.ninsky.cronos.account.profile.api.UpdateProfileRequest;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.account.shared.infrastructure.mapping.AccountMapperConfig;
import com.ninsky.cronos.account.shared.infrastructure.mapping.ValueObjectMappings;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserProfileJpaEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * The user aggregate's single mapper: request → command, domain → responses, and the PUT
 * full-replace onto the profile row. Never touches repositories or the security context.
 */
@Mapper(config = AccountMapperConfig.class, uses = {ValueObjectMappings.class, AvatarUrlMapping.class})
public interface UserProfileMapper {

    @Mapping(target = "username", source = "request.username")
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    @Mapping(target = "phoneNumber", source = "request.phoneNumber", qualifiedByName = "toE164")
    @Mapping(target = "expectedVersion", source = "expectedVersion")
    ProfileUpdate toUpdate(UpdateProfileRequest request, ExpectedVersion expectedVersion);

    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "avatarUrl")
    UserResponse toResponse(UserAccount account);

    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "avatarUrl")
    @Mapping(target = "updatedAt", source = "updatedAt")
    AvatarResponse toAvatarResponse(UserAccount account);

    /** PUT semantics: a null in the command clears the column (SET_TO_NULL); everything else on the row is untouched. */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phoneNumber", source = "phoneNumber", qualifiedByName = "e164ToString")
    void applyTo(ProfileUpdate update, @MappingTarget UserProfileJpaEntity profile);
}
