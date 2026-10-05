package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileRulesTest {

    private final UserReadRepository users = mock(UserReadRepository.class);
    private final UserProfileRules rules = new UserProfileRules(users,
            Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneOffset.UTC));

    @Test
    void normalisesValidInput() {
        Violations violations = new Violations();
        UserProfile profile = rules.validate(violations, input("cmendoza", "  Carla.Mendoza@Cronos.MX ", " Carla  María ",
                "+525511223344", "2027-03-31"), null);

        assertThat(violations.isEmpty()).isTrue();
        assertThat(profile.email()).isEqualTo("carla.mendoza@cronos.mx");
        assertThat(profile.firstName()).isEqualTo("Carla María");
        assertThat(profile.jobTitle()).isNull();
    }

    @Test
    void collectsEveryViolation() {
        when(users.emailTaken(eq("taken@cronos.mx"), any())).thenReturn(true);
        Violations violations = new Violations();
        rules.validate(violations, input("ad..min", "taken@cronos.mx", "Carla1", "5512345678", "2026-10-05"), null);

        assertThatThrownBy(violations::throwIfAny).isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.violations()).extracting(ApiException.Violation::field)
                        .containsExactlyInAnyOrder("username", "firstName", "phoneNumber", "accessExpiresAt", "email"));
    }

    @Test
    void rejectsReservedUsernames() {
        Violations violations = new Violations();
        rules.validate(violations, input("Admin", "a@cronos.mx", "Ana", null, null), null);

        assertThatThrownBy(violations::throwIfAny).isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.violations()).extracting(ApiException.Violation::messageKey)
                        .containsExactly("iam.validation.usernameReserved"));
    }

    @Test
    void availabilityNormalisation() {
        assertThat(UserProfileRules.normalizeEmail("no-dot@domain")).isNull();
        assertThat(UserProfileRules.normalizeUsername(" ok_name ")).isEqualTo("ok_name");
        assertThat(List.of("-x", "a")).allSatisfy(u -> assertThat(UserProfileRules.normalizeUsername(u)).isNull());
    }

    private static UserProfileRules.Input input(String username, String email, String firstName, String phone, String expires) {
        return new UserProfileRules.Input(username, email, firstName, "Mendoza", phone, "  ", null, null, "es-MX",
                expires == null ? null : LocalDate.parse(expires), null);
    }
}
