package com.seatbook.model.dto;

import java.util.List;

/** Unknown JSON fields (e.g. a spoofed user_id) are ignored: identity comes from the token only. */
public record ReserveRequest(List<String> seats, String idempotencyKey) {}
