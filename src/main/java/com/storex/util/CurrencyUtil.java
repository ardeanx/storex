package com.storex.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class CurrencyUtil {

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        String symbol = AppConfig.get("currency_symbol", "Rp");

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("###,###,###", symbols);
        return symbol + " " + df.format(amount);
    }
}
