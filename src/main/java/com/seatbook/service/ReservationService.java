package com.seatbook.service;

import com.seatbook.concurrency.LockCoordinator;
import com.seatbook.exception.ApiException;
import com.seatbook.model.*;
import com.seatbook.repository.IdempotencyRepository;
import com.seatbook.repository.ReservationRepository;
import com.seatbook.repository.SeatRepository;
import com.seatbook.repository.ShowRepository;
import com.seatbook.repository.UserShowCountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_SEATS_PER_REQUEST = 50;
    private static final int MAX_ATTEMPTS = 5;
    public static final List<String> REASONS = List.of(
            "seat-taken", "per-user-limit", "idempotent-replay", "idempotency-conflict", "unknown-seat", "invalid-request");

    public record Outcome(ReservationEntity reservation, boolean replay) {}
    private record CancelResult(ReservationEntity reservation, boolean changed) {}

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final UserShowCountRepository userShowCountRepository;
    private final LockCoordinator locks;
    private final TransactionTemplate transactionTemplate; // READ COMMITTED, JPA transaction manager
    private final Counter confirmed;
    private final Counter cancelled;
    private final Map<String, Counter> declined = new ConcurrentHashMap<>();

    public ReservationService(ShowRepository showRepository, SeatRepository seatRepository, ReservationRepository reservationRepository,
                              IdempotencyRepository idempotencyRepository, UserShowCountRepository userShowCountRepository,
                              LockCoordinator locks, PlatformTransactionManager tm, MeterRegistry reg) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.userShowCountRepository = userShowCountRepository;
        this.locks = locks;
        this.transactionTemplate = new TransactionTemplate(tm);

        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.confirmed = Counter.builder("reservations.confirmed").description("Reservations confirmed").register(reg);
        this.cancelled = Counter.builder("reservations.cancelled").description("Reservations cancelled").register(reg);
        for (String r : REASONS)
            declined.put(r, Counter.builder("reservations.declined").description("Declined/replayed reserve requests by reason")
                    .tag("reason", r).register(reg));
    }

    @Transactional
    public Outcome reserve(String userId, UUID showId, List<String> rawSeats, String key) {
        try {
            Outcome o = doReserve(userId, showId, rawSeats, key);
            
            if (o.replay()) declined.get("idempotent-replay").increment();
            
            else confirmed.increment();
            
            return o;
        } 
        catch (ApiException e) {
            Counter c = declined.get(e.reason);
            if (c != null) c.increment();
            throw e;
        }
    }

    private Outcome doReserve(String userId, UUID showId, List<String> rawSeats, String key) {
        
        if (key == null || key.isBlank() || key.length() > 200)
            throw new ApiException(400, "invalid-request", "idempotency key required (header Idempotency-Key or body idempotency_key, <=200 chars)");
       
        if (rawSeats == null || rawSeats.isEmpty() || rawSeats.size() > MAX_SEATS_PER_REQUEST)
            throw new ApiException(400, "invalid-request", "seats required (1-" + MAX_SEATS_PER_REQUEST + ")");
        
        List<String> labels = new ArrayList<>(new TreeSet<>(rawSeats.stream().map(s -> s == null ? "" : s.trim()).toList()));
        
        if (labels.get(0).isEmpty()) 
            throw new ApiException(400, "invalid-request", "blank seat label");
        
        String hash = sha256(showId + "|" + String.join(",", labels)); // sorted => order-insensitive

        // 1) Replay fast-path (before any seat check, so a retry of a successful request never looks "taken").
        Optional<ReservationEntity> prior = findReplay(userId, key, hash);
       
        if (prior.isPresent()) 
            return new Outcome(prior.get(), true);

        ShowEntity show = showRepository.findById(showId).orElseThrow(() -> new ApiException(404, "not-found", "show not found"));
       
        if (labels.size() > show.getPerUserLimit())
            throw new ApiException(409, "per-user-limit", "request exceeds per-user limit of " + show.getPerUserLimit());

        // 2) Cheap lock-free early decline for seats already gone (authoritative check is under the DB row lock).
        if (seatRepository.anyNotAvailable(showId, labels))
            throw new ApiException(409, "seat-taken", "one or more requested seats are not available");

        // 3) Queue contended requests in the JVM (no DB connection held), then run the authoritative DB transaction.
        return locks.withLocks(lockKeys(showId, userId, labels), () -> {
            // Re-check after waiting: the previous holder may have confirmed this very request (same key) or taken the seat.
            Optional<ReservationEntity> again = findReplay(userId, key, hash);
            
            if (again.isPresent()) return new Outcome(again.get(), true);
            
            if (seatRepository.anyNotAvailable(showId, labels))
                throw new ApiException(409, "seat-taken", "one or more requested seatRepository are not available");
           
            return withRetry(() -> transactionTemplate.execute(st -> reserveInTx(userId, show, labels, key, hash)));
        });
    }

    private Outcome reserveInTx(String userId, ShowEntity show, List<String> labels, String key, String hash) {
        UUID showId = show.getId();
        UUID resId = UUID.randomUUID();

        // Idempotency gate. The JVM lock serialises same-user same-key within an instance; across instances the PK makes
        // a racing duplicate insert fail (unique violation) -> whole tx rolls back -> retry finds the committed row -> replay.
        IdempotencyKeyId kid = new IdempotencyKeyId(userId, key);
        
        Optional<IdempotencyKeyEntity> existing = idempotencyRepository.findById(kid);
        
        if (existing.isPresent()) 
            return new Outcome(replayOf(existing.get(), hash), true);

        idempotencyRepository.saveAndFlush(new IdempotencyKeyEntity(kid, hash, resId));

        // PESSIMISTIC lock on the user's counter row (created on first use; a racing creator hits the PK and retries).
        UserShowCountEntity counter = userShowCountRepository.lockByShowAndUser(showId, userId)
                .orElseGet(() -> userShowCountRepository.saveAndFlush(new UserShowCountEntity(showId, userId)));

        if (counter.getActive() + labels.size() > show.getPerUserLimit())
            throw new ApiException(409, "per-user-limit", "per-user limit of " + show.getPerUserLimit() + " seats would be exceeded");

        // THE atomic decision: PESSIMISTIC lock on seat rows (sorted), check state under the lock, then mutate.
        List<SeatEntity> locked = seatRepository.lockByLabels(showId, labels);

        if (locked.size() != labels.size())
            throw new ApiException(422, "unknown-seat", "one or more seats do not exist in this show");

        for (SeatEntity s : locked)
            if (s.getStatus() != SeatStatus.AVAILABLE)
                throw new ApiException(409, "seat-taken", "seat " + s.getLabel() + " is not available");

        for (SeatEntity s : locked) s.confirm(resId);          // flushed at commit with "WHERE version = ?" (optimistic net)

        counter.setActive(counter.getActive() + labels.size());

        long amount = Math.multiplyExact(show.getPricePaise(), (long) labels.size()); // integer paise

        ReservationEntity res = new ReservationEntity(resId, showId, userId, labels, amount);

        reservationRepository.save(res);

        return new Outcome(res, false);
    }

    // ------------------------------------------------------------------ cancel

    public ReservationEntity cancel(String userId, UUID id) {

        ReservationEntity first = reservationRepository.findById(id).orElseThrow(() -> new ApiException(404, "not-found", "reservation not found"));

        if (!first.getUserId().equals(userId))
            throw new ApiException(403, "forbidden", "only the owner may cancel");

        CancelResult result = locks.withLocks(lockKeys(first.getShowId(), userId, first.getSeats()),
                () -> withRetry(() -> transactionTemplate.execute(st -> cancelInTx(userId, id))));

        if (result.changed()) cancelled.increment(); // only after a successful commit

        return result.reservation();
    }

    private CancelResult cancelInTx(String userId, UUID id) {

        ReservationEntity r = reservationRepository.findById(id).orElseThrow(() -> new ApiException(404, "not-found", "reservation not found"));

        if (!r.getUserId().equals(userId))
            throw new ApiException(403, "forbidden", "only the owner may cancel");

        if (r.getStatus() == ReservationStatus.CANCELLED)
            return new CancelResult(r, false);

        // same lock order as reserve: user counter -> seats (sorted)
        Optional<UserShowCountEntity> counter = userShowCountRepository.lockByShowAndUser(r.getShowId(), userId);
        // Only seats still bound to THIS reservation are freed => can never touch a seat re-sold to someone else.
        List<SeatEntity> mine = seatRepository.lockByReservation(r.getShowId(), id);

        for (SeatEntity s : mine)
            s.release();

        r.cancel();

        counter.ifPresent(c -> c.setActive(Math.max(0, c.getActive() - mine.size())));

        return new CancelResult(r, true);
    }

    // ------------------------------------------------------------------ helpers

    /** Re-runs the body on lock/optimistic/unique-violation failures; each attempt is a brand-new transaction. */
    private <T> T withRetry(Supplier<T> body) {
        for (int attempt = 1; ; attempt++) {

            try {

                return body.get();
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {

                if (attempt >= MAX_ATTEMPTS) throw new ApiException(503, "busy", "please retry");

                log.warn("retrying after {} (attempt {})", e.getClass().getSimpleName(), attempt);

                try {

                    Thread.sleep(ThreadLocalRandom.current().nextInt(5, 30) * attempt);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static List<String> lockKeys(UUID showId, String userId, Collection<String> labels) {

        List<String> keys = new ArrayList<>(labels.size() + 1);

        keys.add("u:" + showId + ":" + userId);

        for (String l : labels)
            keys.add("s:" + showId + ":" + l);

        return keys;
    }

    private Optional<ReservationEntity> findReplay(String userId, String key, String hash) {

        return idempotencyRepository.findById(new IdempotencyKeyId(userId, key)).map(rec -> replayOf(rec, hash));
    }

    private ReservationEntity replayOf(IdempotencyKeyEntity rec, String hash) {

        if (!rec.getRequestHash().equals(hash))
            throw new ApiException(409, "idempotency-conflict", "idempotency key already used with a different request");

        return reservationRepository.findById(rec.getReservationId())
                .orElseThrow(() -> new ApiException(404, "not-found", "reservation not found"));
    }

    private static String sha256(String s) {
        try {

            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
