package com.devlil0.spending_system.service;

import java.time.LocalDateTime;

public record SpendingPeriod(LocalDateTime start, LocalDateTime end, String label) {
}
