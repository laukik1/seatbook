package com.seatbook.model.dto;

import com.seatbook.model.SeatCounts;

public record CountsResponse(int available, int held, int confirmed) {

    public static CountsResponse from(SeatCounts c) {

        return new CountsResponse(c.available(), c.held(), c.confirmed());
    }
}
