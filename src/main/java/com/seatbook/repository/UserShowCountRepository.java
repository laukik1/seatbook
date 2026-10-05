package com.seatbook.repository;

import com.seatbook.model.UserShowCountEntity;
import com.seatbook.model.UserShowCountId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserShowCountRepository extends JpaRepository<UserShowCountEntity, UserShowCountId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserShowCountEntity c where c.id.showId = :showId and c.id.userId = :userId")
    Optional<UserShowCountEntity> lockByShowAndUser(@Param("showId") UUID showId, @Param("userId") String userId);
}
