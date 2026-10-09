package com.ninsky.cronos.kitchen.guide;

/** Pan shapes; round ones are measured by diameter, the rest by length (× width). */
public enum PanShape {
    ROUND,
    SPRINGFORM,
    SQUARE,
    RECTANGULAR,
    SHEET,
    LOAF,
    BUNDT,
    MUFFIN;

    public boolean round() {
        return this == ROUND || this == SPRINGFORM || this == BUNDT || this == MUFFIN;
    }
}
