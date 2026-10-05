package com.seatbook.model.dto;

import java.util.List;

public record CreateShowRequest(String name, List<String> seats, Long pricePaise, Integer perUserLimit) {}
