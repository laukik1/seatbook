package com.seatbook.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record SeatId(UUID showId, String label) implements Serializable {}
