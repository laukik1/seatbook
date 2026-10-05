package com.seatbook.model.dto;

import com.seatbook.model.ShowSnapshot;

import java.util.List;

public record ShowResponse(String id, String name, long pricePaise, int perUserLimit, int totalSeats,
                           int available, int held, int confirmed, CountsResponse counts, List<SeatResponse> seats) {

    public static ShowResponse from(ShowSnapshot s) {

        var sh = s.show();
        var c = s.counts();

        List<SeatResponse> seats = s.seats() == null ? null : s.seats().stream().map(SeatResponse::from).toList();

        return new ShowResponse(sh.getId().toString(), sh.getName(), sh.getPricePaise(), sh.getPerUserLimit(), sh.getTotalSeats(),
                c.available(), c.held(), c.confirmed(), CountsResponse.from(c), seats);
    }
}
