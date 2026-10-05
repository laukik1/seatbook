package com.seatbook.model;

import jakarta.persistence.*;

import java.util.List;
import java.util.UUID;

/** @Version = optimistic lock: two concurrent cancels of the same reservation can't both succeed (loser retries, sees CANCELLED). */
@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    private UUID id;

    private UUID showId;

    private String userId;

    @Convert(converter = StringListConverter.class)
    @Column(name = "seats", nullable = false)
    private List<String> seats;

    private long amountPaise;

    @Convert(converter = ReservationStatusConverter.class)
    @Column(nullable = false)
    private ReservationStatus status;

    @Version
    private Long version;

    protected ReservationEntity() {}

    public ReservationEntity(UUID id, UUID showId, String userId, List<String> seats, long amountPaise) {
        this.id = id; this.showId = showId; this.userId = userId; this.seats = seats;
        this.amountPaise = amountPaise; this.status = ReservationStatus.CONFIRMED;
    }

    public UUID getId() { return id; }
    public UUID getShowId() { return showId; }
    public String getUserId() { return userId; }
    public List<String> getSeats() { return seats; }
    public long getAmountPaise() { return amountPaise; }
    public ReservationStatus getStatus() { return status; }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }
}
