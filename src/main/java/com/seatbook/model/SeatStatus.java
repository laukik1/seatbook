package com.seatbook.model;

import java.util.Locale;

public enum SeatStatus {
    AVAILABLE, HELD, CONFIRMED;

    /** Value stored in the DB / returned in JSON. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SeatStatus fromDb(String s) {
        return valueOf(s.toUpperCase(Locale.ROOT));
    }
}
