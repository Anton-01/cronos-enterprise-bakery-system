package com.ninsky.cronos.application.request.core.auth.validation;

import com.ninsky.cronos.application.request.core.auth.ChangePasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordChangeValidator implements ConstraintValidator<PasswordChange, ChangePasswordRequest> {

    @Override
    public boolean isValid(ChangePasswordRequest request, ConstraintValidatorContext context) {
        if (request == null || request.newPassword() == null || request.newPassword().isEmpty()) {
            return true;
        }
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (request.newPassword().equals(request.currentPassword())) {
            context.buildConstraintViolationWithTemplate("{account.password.sameAsCurrent}")
                    .addPropertyNode("newPassword").addConstraintViolation();
            valid = false;
        }
        if (request.confirmPassword() != null && !request.newPassword().equals(request.confirmPassword())) {
            context.buildConstraintViolationWithTemplate("{account.password.confirmationMismatch}")
                    .addPropertyNode("confirmPassword").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
