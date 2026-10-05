package com.seatbook.service;

import com.seatbook.exception.ApiException;
import com.seatbook.model.SeatCounts;
import com.seatbook.model.SeatEntity;
import com.seatbook.model.ShowEntity;
import com.seatbook.model.ShowSnapshot;
import com.seatbook.model.dto.CreateShowRequest;
import com.seatbook.repository.SeatRepository;
import com.seatbook.repository.ShowRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate snapshotTx;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository, PlatformTransactionManager tm) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.writeTx = new TransactionTemplate(tm);
        // REPEATABLE READ snapshot: counts and per-seat list come from ONE point in time, so
        // available + held + confirmed == total_seats holds even while a burst is mutating rows.
        this.snapshotTx = new TransactionTemplate(tm);
        this.snapshotTx.setReadOnly(true);
        this.snapshotTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Transactional
    public ShowSnapshot create(CreateShowRequest createShowRequest) {

        if (createShowRequest == null || createShowRequest.name() == null || createShowRequest.name().isBlank())
            throw new ApiException(400, "invalid-request", "name required");

        if (createShowRequest.seats() == null || createShowRequest.seats().isEmpty())
            throw new ApiException(400, "invalid-request", "seats required");

        if (createShowRequest.pricePaise() == null || createShowRequest.pricePaise() < 0)
            throw new ApiException(400, "invalid-request", "price_paise must be a non-negative integer");

        int limit = createShowRequest.perUserLimit() == null ? 4 : createShowRequest.perUserLimit();

        if (limit < 1) throw new ApiException(400, "invalid-request", "per_user_limit must be >= 1");

        List<String> labels = createShowRequest.seats().stream().map(s -> s == null ? "" : s.trim()).toList();

        if (labels.stream().anyMatch(s -> s.isEmpty() || s.length() > 32 || s.contains(",")))
            throw new ApiException(400, "invalid-request", "seat labels must be 1-32 chars and must not contain commas");

        if (new HashSet<>(labels).size() != labels.size())
            throw new ApiException(400, "invalid-request", "duplicate seat labels");

        ShowEntity show = new ShowEntity(UUID.randomUUID(), createShowRequest.name().trim(), createShowRequest.pricePaise(), limit, labels.size());

        writeTx.executeWithoutResult(st -> {

            showRepository.save(show);

            seatRepository.saveAll(labels.stream().map(l -> new SeatEntity(show.getId(), l)).toList());
        });

        return get(show.getId(), true);
    }

    public ShowSnapshot get(UUID id, boolean includeSeats) {

        return snapshotTx.execute(st -> {

            ShowEntity show = showRepository.findById(id).orElseThrow(() -> new ApiException(404, "not-found", "show not found"));

            SeatCounts counts = seatRepository.countsForShow(id);

            List<SeatEntity> list = includeSeats ? seatRepository.findAllByShow(id) : null;

            return new ShowSnapshot(show, counts, list);
        });
    }
}
