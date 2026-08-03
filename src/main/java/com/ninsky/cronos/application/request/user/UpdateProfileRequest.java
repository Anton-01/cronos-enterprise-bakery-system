package com.ninsky.cronos.application.request.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        @Size(max = 255, message = "Business name must not exceed 255 characters")
        String businessName,

        @Size(max = 100, message = "Business type must not exceed 100 characters")
        String businessType
) {
    public UpdateProfileRequest {
        firstName = firstName != null ? firstName.trim() : null;
        lastName = lastName != null ? lastName.trim() : null;
        phoneNumber = phoneNumber != null ? phoneNumber.trim() : null;
        businessName = businessName != null ? businessName.trim() : null;
        businessType = businessType != null ? businessType.trim() : null;
    }
}
