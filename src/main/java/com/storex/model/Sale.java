package com.storex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Sale(
        int id,
        LocalDateTime saleDate,
        BigDecimal totalAmount,
        BigDecimal cashAmount,
        BigDecimal changeAmount,
        int userId,
        String status
) {}