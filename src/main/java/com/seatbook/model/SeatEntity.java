package com.seatbook.model;

import jakarta.persistence.*;

import java.util.UUID;


@Entity
@Table(name = "seats")
public class SeatEntity {

    @EmbeddedId
    private SeatId id;

    @Convert(converter = SeatStatusConverter.class)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private UUID reservationId;

    @Version
    private Long version;

    protected SeatEntity() {}

    public SeatEntity(UUID showId, String label)
    { this.id = new SeatId(showId, label); }

    public String getLabel() { return id.label(); }
    public UUID getShowId() { return id.showId(); }
    public SeatStatus getStatus() { return status; }
    public UUID getReservationId() { return reservationId; }

    public void confirm(UUID reservationId) { this.status = SeatStatus.CONFIRMED; this.reservationId = reservationId; }

    public void release() { this.status = SeatStatus.AVAILABLE; this.reservationId = null; }

}
