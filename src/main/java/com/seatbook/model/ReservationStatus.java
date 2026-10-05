package com.seatbook.model;

import java.util.Locale;

public enum ReservationStatus {
    CONFIRMED, CANCELLED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ReservationStatus fromDb(String s) {
        return valueOf(s.toUpperCase(Locale.ROOT));
    }
}
