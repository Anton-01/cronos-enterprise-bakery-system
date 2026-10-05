package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;

/** A line's rounded amounts; {@code net + tax == gross} always. */
public record LineAmounts(BigDecimal net, BigDecimal tax, BigDecimal gross) {
}
