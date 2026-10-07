package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;

import java.util.Optional;

/** Base dimension an ingredient is costed in: g (MASS), ml (VOLUME), pz (COUNT). */
public enum Dimension {
    MASS,
    VOLUME,
    COUNT;

    public UnitDimension unitDimension() {
        return UnitDimension.valueOf(name());
    }

    public static Optional<Dimension> of(UnitDimension dimension) {
        return switch (dimension) {
            case MASS -> Optional.of(MASS);
            case VOLUME -> Optional.of(VOLUME);
            case COUNT -> Optional.of(COUNT);
            case LENGTH -> Optional.empty();
        };
    }
}
