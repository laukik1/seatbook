package com.seatbook.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @EmbeddedId
    private IdempotencyKeyId id;

    private String requestHash;

    private UUID reservationId;

    @Version
    private Long version;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(IdempotencyKeyId id, String requestHash, UUID reservationId) {
        this.id = id; this.requestHash = requestHash; this.reservationId = reservationId;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getReservationId() { return reservationId; }
}
