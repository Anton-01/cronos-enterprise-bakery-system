package com.ninsky.cronos.quote.domain;

/** Why a quote was rejected; {@code OTHER} needs a comment and clients cannot pick {@code NO_RESPONSE}. */
public enum QuoteRejectReason { PRICE, DATE_UNAVAILABLE, CHOSE_COMPETITOR, NO_RESPONSE, CHANGED_PLANS, OTHER }
