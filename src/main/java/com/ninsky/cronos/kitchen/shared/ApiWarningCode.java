package com.ninsky.cronos.kitchen.shared;

/** Non-blocking warnings of the kitchen contract (§8). */
public enum ApiWarningCode {
    PRICE_JUMP,
    BELOW_TARGET_MARGIN,
    UNPRICED_LINES,
    EMPTY_PROCESS
}
