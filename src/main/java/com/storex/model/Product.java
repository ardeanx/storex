package com.storex.model;

import java.math.BigDecimal;

public record Product(
        int id,
        String barcode,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String category
) {}