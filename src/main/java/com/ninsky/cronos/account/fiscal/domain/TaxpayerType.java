package com.ninsky.cronos.account.fiscal.domain;

/** Persona física (13-char RFC) vs. persona moral (12-char RFC). Never client-supplied: derived from the RFC. */
public enum TaxpayerType {
    INDIVIDUAL,
    LEGAL_ENTITY
}
