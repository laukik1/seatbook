package com.seatbook.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** List<String> <-> comma-joined TEXT. Seat labels are validated to never contain a comma. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> l) {
        return l == null ? null : String.join(",", l);
    }
    @Override
    public List<String> convertToEntityAttribute(String s) {
        return s == null || s.isEmpty() ? List.of() : Arrays.asList(s.split(","));
    }
}
