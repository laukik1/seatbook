package com.seatbook.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReservationStatus s) {
        return s == null ? null : s.value();
    }

    @Override
    public ReservationStatus convertToEntityAttribute(String s) {
        return s == null ? null : ReservationStatus.fromDb(s);
    }
}
