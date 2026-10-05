package com.seatbook.repository;

import com.seatbook.model.ShowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowRepository extends JpaRepository<ShowEntity, UUID> {}
