package com.ninsky.cronos.account.fiscal.domain;

/**
 * What an RFC says about its holder, produced by {@link Rfc#identity()}. Sealed so every decision
 * that depends on the taxpayer kind (regime applicability, {@link TaxpayerType}) is an exhaustive
 * {@code switch} — a third kind would be a compile error everywhere it matters.
 */
public sealed interface TaxpayerIdentity permits TaxpayerIdentity.Individual, TaxpayerIdentity.LegalEntity {

    Rfc rfc();

    default TaxpayerType type() {
        return switch (this) {
            case Individual ignored -> TaxpayerType.INDIVIDUAL;
            case LegalEntity ignored -> TaxpayerType.LEGAL_ENTITY;
        };
    }

    /** Persona física: 4-letter name prefix. */
    record Individual(Rfc rfc) implements TaxpayerIdentity {
    }

    /** Persona moral: 3-letter company prefix. */
    record LegalEntity(Rfc rfc) implements TaxpayerIdentity {
    }
}
