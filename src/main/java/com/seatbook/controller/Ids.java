package com.seatbook.controller;

import com.seatbook.exception.ApiException;

import java.util.UUID;

final class Ids {
    private Ids() {}

    static UUID parse(String s, String what) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new ApiException(404, "not-found", what + " not found");
        }
    }
}
