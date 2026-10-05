package com.seatbook.concurrency;

import com.seatbook.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-JVM striped locks: the FIRST line of defence against a stampede, NOT the source of correctness.
 *
 * Why: when 500 requests fight for seat A12, without this all 500 would each take a DB connection and queue on the
 * row lock, exhausting the pool. Here they queue on a cheap JVM lock WITHOUT holding a connection; only one at a time
 * reaches the database, and the rest are declined quickly after the winner commits.
 *
 * Correctness across multiple app instances still comes from the database (pessimistic row locks, @Version,
 * primary-key uniqueness). This class only reduces contention within one instance.
 *
 * Deadlock freedom: all requested keys are mapped to stripe indexes, de-duplicated and acquired in ASCENDING order
 * (one global total order across user keys and seat keys); released in reverse order.
 */
@Component
public class LockCoordinator {

    private static final int STRIPES = 4096;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 60;

    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    public LockCoordinator() {
        for (int i = 0; i < STRIPES; i++) stripes[i] = new ReentrantLock();
    }

    private static int stripeOf(String key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return (h & 0x7fffffff) % STRIPES;
    }

    public <T> T withLocks(Collection<String> keys, Supplier<T> body) {
        int[] order = keys.stream().mapToInt(LockCoordinator::stripeOf).distinct().sorted().toArray();
        int held = 0;
        try {
            for (int idx : order) {
                if (!stripes[idx].tryLock(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    throw new ApiException(503, "busy", "timed out waiting for contended resource, please retry");
                held++;
            }
            return body.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "busy", "interrupted, please retry");
        } finally {
            for (int i = held - 1; i >= 0; i--) stripes[order[i]].unlock();
        }
    }
}
