package com.ninsky.cronos.kitchen.unit;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;

import java.math.BigDecimal;

/** The slice of a measurement unit the kitchen needs; multiplier is relative to its dimension's base. */
public record UnitInfo(long id, String code, String name, UnitDimension dimension, BigDecimal multiplierToBase, boolean active) {
}
