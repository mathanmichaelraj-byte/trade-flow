package com.stocksense.market;

import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic offline data (seeded random walk with slowly drifting trend). It exists so the whole
 * stack runs without internet or API keys. Everything it returns is labelled source="simulated".
 */
@Component
public class SimulatedProvider implements MarketDataProvider {

    private record Entry(String symbol, String name, String sector, String industry, String exchange,
                         String currency, String country, double startPrice, double annualVol, double annualDrift,
                         double sharesBillions, double pe, double divYieldPct) {
    }

    private static final LocalDate START = LocalDate.of(2019, 1, 1);

    private static final List<Entry> CATALOG = List.of(
            new Entry("AAPL", "Apple Inc.", "Technology", "Consumer Electronics", "NASDAQ", "USD", "United States", 40, 0.30, 0.22, 15.5, 30, 0.5),
            new Entry("MSFT", "Microsoft Corporation", "Technology", "Software", "NASDAQ", "USD", "United States", 100, 0.28, 0.24, 7.4, 34, 0.8),
            new Entry("GOOGL", "Alphabet Inc.", "Communication Services", "Internet Content", "NASDAQ", "USD", "United States", 52, 0.29, 0.20, 12.3, 25, 0.0),
            new Entry("AMZN", "Amazon.com, Inc.", "Consumer Cyclical", "Internet Retail", "NASDAQ", "USD", "United States", 85, 0.34, 0.19, 10.5, 45, 0.0),
            new Entry("NVDA", "NVIDIA Corporation", "Technology", "Semiconductors", "NASDAQ", "USD", "United States", 4, 0.50, 0.45, 24.5, 55, 0.03),
            new Entry("TSLA", "Tesla, Inc.", "Consumer Cyclical", "Auto Manufacturers", "NASDAQ", "USD", "United States", 20, 0.62, 0.30, 3.2, 70, 0.0),
            new Entry("META", "Meta Platforms, Inc.", "Communication Services", "Internet Content", "NASDAQ", "USD", "United States", 135, 0.40, 0.20, 2.5, 26, 0.4),
            new Entry("NFLX", "Netflix, Inc.", "Communication Services", "Entertainment", "NASDAQ", "USD", "United States", 260, 0.42, 0.18, 0.43, 40, 0.0),
            new Entry("AMD", "Advanced Micro Devices, Inc.", "Technology", "Semiconductors", "NASDAQ", "USD", "United States", 18, 0.52, 0.30, 1.6, 60, 0.0),
            new Entry("JPM", "JPMorgan Chase & Co.", "Financial Services", "Banks", "NYSE", "USD", "United States", 100, 0.26, 0.12, 2.8, 12, 2.3),
            new Entry("V", "Visa Inc.", "Financial Services", "Credit Services", "NYSE", "USD", "United States", 135, 0.24, 0.13, 2.0, 30, 0.8),
            new Entry("WMT", "Walmart Inc.", "Consumer Defensive", "Discount Stores", "NYSE", "USD", "United States", 47, 0.20, 0.10, 8.0, 28, 1.3),
            new Entry("KO", "The Coca-Cola Company", "Consumer Defensive", "Beverages", "NYSE", "USD", "United States", 46, 0.17, 0.07, 4.3, 25, 3.0),
            new Entry("DIS", "The Walt Disney Company", "Communication Services", "Entertainment", "NYSE", "USD", "United States", 110, 0.30, 0.03, 1.8, 35, 0.8),
            new Entry("RELIANCE.NS", "Reliance Industries Limited", "Energy", "Oil & Gas Refining", "NSE", "INR", "India", 1250, 0.26, 0.16, 6.8, 26, 0.4),
            new Entry("TCS.NS", "Tata Consultancy Services Limited", "Technology", "IT Services", "NSE", "INR", "India", 1900, 0.23, 0.14, 3.6, 30, 1.6),
            new Entry("INFY.NS", "Infosys Limited", "Technology", "IT Services", "NSE", "INR", "India", 700, 0.26, 0.14, 4.1, 24, 2.4),
            new Entry("HDFCBANK.NS", "HDFC Bank Limited", "Financial Services", "Banks", "NSE", "INR", "India", 1150, 0.22, 0.10, 7.6, 19, 1.1),
            new Entry("ICICIBANK.NS", "ICICI Bank Limited", "Financial Services", "Banks", "NSE", "INR", "India", 380, 0.28, 0.20, 7.0, 18, 0.8),
            new Entry("SBIN.NS", "State Bank of India", "Financial Services", "Banks", "NSE", "INR", "India", 290, 0.33, 0.20, 8.9, 10, 1.7),
            new Entry("ITC.NS", "ITC Limited", "Consumer Defensive", "Tobacco", "NSE", "INR", "India", 280, 0.22, 0.14, 12.5, 26, 3.2),
            new Entry("TATAMOTORS.NS", "Tata Motors Limited", "Consumer Cyclical", "Auto Manufacturers", "NSE", "INR", "India", 180, 0.42, 0.16, 3.7, 14, 0.3));

    private record Series(LocalDate lastDay, List<Bar> bars) {
    }

    private final Map<String, Series> cache = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<SearchHit> hits = new ArrayList<>();
        for (Entry e : CATALOG) {
            if (e.symbol.toLowerCase(Locale.ROOT).contains(q) || e.name.toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(new SearchHit(e.symbol, e.name, e.exchange, "EQUITY"));
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        return hits;
    }

    @Override
    public QuoteData quote(String symbol) {
        Entry e = entry(symbol);
        List<Bar> bars = series(e);
        Bar last = bars.get(bars.size() - 1);
        Bar prev = bars.get(bars.size() - 2);
        return new QuoteData(e.symbol, e.name, last.close(), prev.close(), last.open(), last.high(), last.low(),
                last.volume(), e.currency, e.exchange, name(), Instant.now());
    }

    @Override
    public List<Bar> history(String symbol, int days) {
        List<Bar> all = series(entry(symbol));
        LocalDate cutoff = all.get(all.size() - 1).date().minusDays(days);
        List<Bar> out = new ArrayList<>();
        for (Bar b : all) {
            if (!b.date().isBefore(cutoff)) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    public CompanyProfile profile(String symbol) {
        Entry e = entry(symbol);
        List<Bar> bars = series(e);
        double price = bars.get(bars.size() - 1).close();
        double hi = 0, lo = Double.MAX_VALUE;
        for (Bar b : bars.subList(Math.max(0, bars.size() - 252), bars.size())) {
            hi = Math.max(hi, b.high());
            lo = Math.min(lo, b.low());
        }
        return new CompanyProfile(e.symbol, e.name, e.sector, e.industry, e.exchange, e.currency, e.country, null,
                "Simulated profile for " + e.name + ". Figures are synthetic and for demonstration only.",
                price * e.sharesBillions * 1e9, e.pe, price / e.pe, e.divYieldPct, 0.8 + e.annualVol, hi, lo,
                8 + Math.floorMod(e.symbol.hashCode(), 2200) / 100.0, 10 + Math.floorMod(e.symbol.hashCode() / 7, 2000) / 100.0,
                name());
    }

    private Entry entry(String symbol) {
        for (Entry e : CATALOG) {
            if (e.symbol.equalsIgnoreCase(symbol)) {
                return e;
            }
        }
        throw new StockSenseException.SymbolNotFound(symbol);
    }

    private List<Bar> series(Entry e) {
        LocalDate lastDay = lastBusinessDay(LocalDate.now(ZoneOffset.UTC));
        Series cached = cache.get(e.symbol);
        if (cached != null && cached.lastDay.equals(lastDay)) {
            return cached.bars;
        }
        List<Bar> bars = generate(e, lastDay);
        cache.put(e.symbol, new Series(lastDay, bars));
        return bars;
    }

    private static List<Bar> generate(Entry e, LocalDate lastDay) {
        Random r = new Random(e.symbol.hashCode() * 31L + 7);
        double daily = e.annualVol / Math.sqrt(252);
        double drift = e.annualDrift / 252;
        double regime = 0;
        double price = e.startPrice;
        List<Bar> bars = new ArrayList<>();
        for (LocalDate d = START; !d.isAfter(lastDay); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            regime = 0.97 * regime + r.nextGaussian() * 0.0006;
            double ret = drift + regime + daily * r.nextGaussian();
            double open = price * (1 + r.nextGaussian() * daily * 0.3);
            double close = Math.max(0.5, price * Math.exp(ret));
            double high = Math.max(open, close) * (1 + Math.abs(r.nextGaussian()) * daily * 0.4);
            double low = Math.min(open, close) * (1 - Math.abs(r.nextGaussian()) * daily * 0.4);
            long volume = (long) (2_000_000 * (1 + 0.4 * Math.abs(r.nextGaussian())) * (1 + 8 * Math.abs(ret)));
            bars.add(new Bar(d, round(open), round(high), round(low), round(close), volume));
            price = close;
        }
        return bars;
    }

    private static LocalDate lastBusinessDay(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
