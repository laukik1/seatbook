package com.seatbook.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "user_show_counts")
public class UserShowCountEntity {

    @EmbeddedId
    private UserShowCountId id;

    private int active;

    @Version
    private Long version;

    protected UserShowCountEntity() {}

    public UserShowCountEntity(UUID showId, String userId) { this.id = new UserShowCountId(showId, userId); }

    public int getActive() { return active; }

    public void setActive(int active) { this.active = active; }
}
