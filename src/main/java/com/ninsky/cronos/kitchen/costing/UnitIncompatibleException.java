package com.ninsky.cronos.kitchen.costing;

/** A unit cannot be expressed in the ingredient's base dimension (no density bridge). */
public class UnitIncompatibleException extends RuntimeException {

    public UnitIncompatibleException(String message) {
        super(message);
    }
}
