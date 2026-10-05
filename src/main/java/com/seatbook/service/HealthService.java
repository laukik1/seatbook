package com.seatbook.service;

import com.seatbook.repository.HealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthRepository healthRepository;

    public boolean isReady() {

        return healthRepository.isDatabaseUp();
    }
}
