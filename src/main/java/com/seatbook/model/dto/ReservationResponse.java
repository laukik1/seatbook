package com.seatbook.model.dto;

import com.seatbook.model.ReservationEntity;

import java.util.List;

public record ReservationResponse(String reservationId, String showId, String userId, List<String> seats,
                                  long amountPaise, String status) {

    public static ReservationResponse from(ReservationEntity r) {

        return new ReservationResponse(r.getId().toString(), r.getShowId().toString(), r.getUserId(), r.getSeats(),
                r.getAmountPaise(), r.getStatus().value());
    }
}
