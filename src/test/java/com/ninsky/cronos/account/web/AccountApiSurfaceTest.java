package com.ninsky.cronos.account.web;

import com.ninsky.cronos.account.avatar.api.AvatarController;
import com.ninsky.cronos.account.fiscal.api.AddressRequest;
import com.ninsky.cronos.account.fiscal.api.FiscalDataController;
import com.ninsky.cronos.account.fiscal.api.UpsertFiscalDataRequest;
import com.ninsky.cronos.account.profile.api.ProfileController;
import com.ninsky.cronos.account.profile.api.UpdateProfileRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "A user can never touch another user's data" holds structurally: the self-service endpoints have
 * no path/query parameter that could name a user, and no request body carries identity or
 * privilege fields. The only identity source is the authenticated principal (CurrentUserProvider).
 */
class AccountApiSurfaceTest {

    private static final Set<String> FORBIDDEN_BODY_FIELDS =
            Set.of("id", "userId", "email", "roles", "enabled", "accountNonLocked", "taxpayerType", "updatedAt", "version");

    @ParameterizedTest
    @ValueSource(classes = {ProfileController.class, AvatarController.class, FiscalDataController.class})
    void selfServiceControllersTakeNoUserIdentifyingParameters(Class<?> controller) {
        assertThat(Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .filter(p -> p.isAnnotationPresent(PathVariable.class) || p.isAnnotationPresent(RequestParam.class)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(classes = {UpdateProfileRequest.class, UpsertFiscalDataRequest.class, AddressRequest.class})
    void requestBodiesCannotCarryIdentityOrPrivilegeFields(Class<?> dto) {
        assertThat(Arrays.stream(dto.getRecordComponents()).map(RecordComponent::getName)).doesNotContainAnyElementsOf(FORBIDDEN_BODY_FIELDS);
    }
}
