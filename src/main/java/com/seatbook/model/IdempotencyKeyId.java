package com.seatbook.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record IdempotencyKeyId(String userId, String idemKey) implements Serializable {}
