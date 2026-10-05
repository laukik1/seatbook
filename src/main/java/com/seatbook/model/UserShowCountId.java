package com.seatbook.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record UserShowCountId(UUID showId, String userId) implements Serializable {}
