package com.ninsky.cronos.account.profile.api;

import com.ninsky.cronos.account.profile.api.validation.E164;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.shared.api.RejectUnknownProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/me body. Full replace: every field is always sent and {@code null} clears it.
 * Deliberately has no {@code email}, {@code roles}, {@code enabled}, ... — and unknown properties
 * are rejected ({@link RejectUnknownProperties}), so they cannot be mass-assigned.
 */
@RejectUnknownProperties
@Schema(name = "UpdateProfileRequest", example = """
        {"username": "admin_cronos", "firstName": "Antón", "lastName": null, "phoneNumber": "+14155552671"}""")
public record UpdateProfileRequest(
        @Schema(example = "admin_cronos", minLength = 3, maxLength = 50)
        @NotBlank(message = "{account.profile.username.required}")
        @Size(min = ProfileUpdate.USERNAME_MIN, max = ProfileUpdate.USERNAME_MAX, message = "{account.profile.username.size}")
        String username,

        @Schema(example = "Antón", nullable = true, maxLength = 100)
        @Size(max = ProfileUpdate.NAME_MAX, message = "{account.profile.firstName.size}")
        String firstName,

        @Schema(example = "Admin", nullable = true, maxLength = 100)
        @Size(max = ProfileUpdate.NAME_MAX, message = "{account.profile.lastName.size}")
        String lastName,

        @Schema(example = "+525512345678", nullable = true, description = "E.164")
        @E164
        String phoneNumber
) {
    public UpdateProfileRequest {
        username = username == null ? null : username.strip();
        firstName = ProfileUpdate.blankToNull(firstName);
        lastName = ProfileUpdate.blankToNull(lastName);
        phoneNumber = ProfileUpdate.blankToNull(phoneNumber);
    }
}
