package com.seatbook.controller;

import com.seatbook.config.AuthService;
import com.seatbook.exception.ApiException;
import com.seatbook.model.dto.ReservationResponse;
import com.seatbook.model.dto.ReserveRequest;
import com.seatbook.service.ReservationService;
import com.seatbook.service.ReservationService.Outcome;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final AuthService authService;

    /** 201 = newly confirmed, 200 (+Idempotent-Replay: true) = retry of an already-processed key, 409 = clean decline. */
    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @RequestHeader(value = "Authorization", required = false) String authz,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
            @PathVariable String id,
            @RequestBody ReserveRequest body) {

        String userId = authService.requireUser(authz); // identity from token ONLY

        String key = headerKey != null ? headerKey : body.idempotencyKey();

        if (headerKey != null && body.idempotencyKey() != null && !headerKey.equals(body.idempotencyKey()))
            throw new ApiException(400, "invalid-request", "Idempotency-Key header and body key differ");

        Outcome o = reservationService.reserve(userId, Ids.parse(id, "show"), body.seats(), key);

        ReservationResponse resp = ReservationResponse.from(o.reservation());

        if (o.replay())
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(resp);

        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@RequestHeader(value = "Authorization", required = false) String authz,
                                      @PathVariable String id) {

        String userId = authService.requireUser(authz);

        return ReservationResponse.from(reservationService.cancel(userId, Ids.parse(id, "reservation")));
    }
}
