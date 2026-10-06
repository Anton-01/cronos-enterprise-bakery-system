package com.ninsky.cronos.iam.twofactor.api;

import java.util.List;

/** Contract §8.2 {@code TwoFactorRecoveryCodes}: the plain codes are shown once and never logged. */
public record TwoFactorRecoveryCodes(List<String> recoveryCodes, TwoFactorStatus status) {

    @Override
    public String toString() {
        return "TwoFactorRecoveryCodes[count=" + recoveryCodes.size() + ", status=" + status + "]";
    }
}
