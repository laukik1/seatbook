package com.seatbook.config;

import com.seatbook.model.SeatCounts;
import com.seatbook.repository.SeatRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SeatMetrics {
    private final SeatRepository seatRepository;
    private volatile long ts;
    private volatile double[] cache = {Double.NaN, Double.NaN, Double.NaN};

    public SeatMetrics(MeterRegistry reg, SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
        Gauge.builder("seats.available", this, m -> m.snap()[0]).description("Seats currently available (all shows)").register(reg);
        Gauge.builder("seats.held", this, m -> m.snap()[1]).description("Seats currently held").register(reg);
        Gauge.builder("seats.confirmed", this, m -> m.snap()[2]).description("Seats currently confirmed").register(reg);
    }

    private synchronized double[] snap() {
        long now = System.currentTimeMillis();
        if (now - ts < 1000) return cache;
        try {
            SeatCounts c = seatRepository.countsOverall();
            cache = new double[]{c.available(), c.held(), c.confirmed()};
        } catch (Exception e) {
            cache = new double[]{Double.NaN, Double.NaN, Double.NaN};
        }
        ts = now;
        return cache;
    }
}
