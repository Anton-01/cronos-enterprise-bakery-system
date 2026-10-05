package com.ninsky.cronos.iam.twofactor.api;

/** {@code code} is a 6-digit TOTP or a recovery code. */
public record RecoveryCodesRequest(String code) {

    @Override
    public String toString() {
        return "RecoveryCodesRequest[***]";
    }
}
