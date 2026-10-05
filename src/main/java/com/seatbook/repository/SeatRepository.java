package com.seatbook.repository;

import com.seatbook.model.SeatCounts;
import com.seatbook.model.SeatEntity;
import com.seatbook.model.SeatId;
import com.seatbook.model.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<SeatEntity, SeatId> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatEntity s where s.id.showId = :showId and s.id.label in :labels order by s.id.label")
    List<SeatEntity> lockByLabels(@Param("showId") UUID showId, @Param("labels") Collection<String> labels);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatEntity s where s.id.showId = :showId and s.reservationId = :reservationId order by s.id.label")
    List<SeatEntity> lockByReservation(@Param("showId") UUID showId, @Param("reservationId") UUID reservationId);

    @Query("select count(s) from SeatEntity s where s.id.showId = :showId and s.id.label in :labels and s.status <> :status")
    long countWithStatusNot(@Param("showId") UUID showId, @Param("labels") Collection<String> labels, @Param("status") SeatStatus status);

    @Query("select s from SeatEntity s where s.id.showId = :showId order by s.id.label")
    List<SeatEntity> findAllByShow(@Param("showId") UUID showId);

    @Query("select s.status, count(s) from SeatEntity s where s.id.showId = :showId group by s.status")
    List<Object[]> countByStatusForShow(@Param("showId") UUID showId);

    @Query("select s.status, count(s) from SeatEntity s group by s.status")
    List<Object[]> countByStatusOverall();

    default boolean anyNotAvailable(UUID showId, Collection<String> labels) {
        return countWithStatusNot(showId, labels, SeatStatus.AVAILABLE) > 0;
    }

    default SeatCounts countsForShow(UUID showId) { return toCounts(countByStatusForShow(showId)); }

    default SeatCounts countsOverall() { return toCounts(countByStatusOverall()); }

    private static SeatCounts toCounts(List<Object[]> rows) {
        int a = 0, h = 0, c = 0;
        for (Object[] r : rows) {
            int n = ((Number) r[1]).intValue();
            switch ((SeatStatus) r[0]) {
                case AVAILABLE -> a = n;
                case HELD -> h = n;
                case CONFIRMED -> c = n;
            }
        }
        return new SeatCounts(a, h, c);
    }
}
