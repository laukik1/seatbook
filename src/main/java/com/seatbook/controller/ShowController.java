package com.seatbook.controller;

import com.seatbook.config.AuthService;
import com.seatbook.model.dto.CreateShowRequest;
import com.seatbook.model.dto.ShowResponse;
import com.seatbook.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;
    private final AuthService authService;

    @PostMapping("/shows")
    public ResponseEntity<ShowResponse> create(@RequestHeader(value = "Authorization", required = false) String authz,
                                               @RequestBody CreateShowRequest body) {
        authService.requireAdmin(authz);
        return ResponseEntity.status(201).body(ShowResponse.from(showService.create(body)));
    }

    @GetMapping("/shows/{id}")
    public ShowResponse get(@PathVariable String id,
                            @RequestParam(name = "include_seats", defaultValue = "true") boolean includeSeats) {
        return ShowResponse.from(showService.get(Ids.parse(id, "show"), includeSeats));
    }
}
