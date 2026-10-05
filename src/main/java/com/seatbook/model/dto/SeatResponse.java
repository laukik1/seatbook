package com.seatbook.model.dto;

import com.seatbook.model.SeatEntity;

public record SeatResponse(String seat, String status) {

    public static SeatResponse from(SeatEntity s) {

        return new SeatResponse(s.getLabel(), s.getStatus().value()); }
}
