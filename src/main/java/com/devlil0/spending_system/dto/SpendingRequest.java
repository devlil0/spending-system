package com.devlil0.spending_system.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SpendingRequest (

    String description,
    BigDecimal amount,
    String category,
    LocalDateTime date

) {}
