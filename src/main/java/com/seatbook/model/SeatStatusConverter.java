package com.seatbook.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SeatStatusConverter implements AttributeConverter<SeatStatus, String> {

    @Override
    public String convertToDatabaseColumn(SeatStatus s) {
        return s == null ? null : s.value();
    }

    @Override
    public SeatStatus convertToEntityAttribute(String s) {
        return s == null ? null : SeatStatus.fromDb(s);
    }
}
