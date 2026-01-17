package com.storex.model;

import java.math.BigDecimal;

public record CartItem(
        int productId,
        String name,
        BigDecimal price,
        int quantity
) {
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}