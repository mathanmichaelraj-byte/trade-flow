package com.stocksense.market;

import java.time.Instant;
import java.time.LocalDate;

/** Provider-neutral market data types. */
public final class MarketModels {

    private MarketModels() {
    }

    public record Bar(LocalDate date, double open, double high, double low, double close, long volume) {
    }

    public record SearchHit(String symbol, String name, String exchange, String type) {
    }

    public record QuoteData(String symbol, String name, double price, double previousClose, double open,
                            double high, double low, long volume, String currency, String exchange,
                            String source, Instant asOf) {
        public double change() {
            return price - previousClose;
        }

        public double changePercent() {
            return previousClose == 0 ? 0 : (price - previousClose) / previousClose * 100.0;
        }
    }

    /** dividendYield, roe and roce are percentages. Any fundamental may be null when the provider lacks it. */
    public record CompanyProfile(String symbol, String name, String sector, String industry, String exchange,
                                 String currency, String country, String website, String description,
                                 Double marketCap, Double peRatio, Double eps, Double dividendYield, Double beta,
                                 Double week52High, Double week52Low, Double roe, Double roce, String source) {
    }

    public record Sourced<T>(T value, String source) {
    }
}
