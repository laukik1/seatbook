package com.seatbook.repository;

import com.seatbook.model.IdempotencyKeyEntity;
import com.seatbook.model.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {}
