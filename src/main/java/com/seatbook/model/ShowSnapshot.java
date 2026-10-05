package com.seatbook.model;

import java.util.List;

/** Consistent point-in-time view of a show (seats is null when not requested). */
public record ShowSnapshot(ShowEntity show, SeatCounts counts, List<SeatEntity> seats) {}
