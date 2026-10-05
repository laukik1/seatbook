package com.seatbook.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "shows")
public class ShowEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private long pricePaise;

    private int perUserLimit;

    private int totalSeats;

    @Version
    private Long version; // null => Spring Data treats the entity as new and persists (assigned id)

    protected ShowEntity() {}

    public ShowEntity(UUID id, String name, long pricePaise, int perUserLimit, int totalSeats) {
        this.id = id; this.name = name; this.pricePaise = pricePaise; this.perUserLimit = perUserLimit; this.totalSeats = totalSeats;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getPricePaise() { return pricePaise; }
    public int getPerUserLimit() { return perUserLimit; }
    public int getTotalSeats() { return totalSeats; }
}
