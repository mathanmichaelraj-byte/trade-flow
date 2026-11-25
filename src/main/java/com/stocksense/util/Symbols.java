package com.stocksense.util;

import com.stocksense.exception.StockSenseException;

import java.util.regex.Pattern;

public final class Symbols {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-^=]{0,14}$");

    private Symbols() {
    }

    /** Trims, upper-cases and validates a ticker symbol (e.g. AAPL, TCS.NS, BRK-B, ^GSPC). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new StockSenseException.InvalidRequest("symbol is required");
        }
        String s = raw.trim().toUpperCase();
        if (!VALID.matcher(s).matches()) {
            throw new StockSenseException.InvalidRequest("Invalid symbol: " + raw.trim());
        }
        return s;
    }
}
