package com.devlil0.spending_system.dto;

import java.math.BigDecimal;

public record SpendingRequest (

    String description,
    BigDecimal amount,
    String category

) {}
