package com.stocksense.service;

import com.stocksense.domain.PriceBar;
import com.stocksense.domain.Stock;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketDataProvider;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;
import com.stocksense.market.MarketModels.Sourced;
import com.stocksense.market.ProviderRouter;
import com.stocksense.repository.PriceBarRepository;
import com.stocksense.repository.StockRepository;
import com.stocksense.util.Symbols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Market-data facade: quotes (short in-memory cache), company profiles and daily history
 * (both persisted in the database and refreshed when stale).
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    public static final int STORE_DAYS = 1100;
    private static final Duration QUOTE_TTL = Duration.ofSeconds(60);
    private static final Duration HISTORY_TTL = Duration.ofHours(6);
    private static final Duration PROFILE_TTL = Duration.ofHours(24);
    private static final String SIMULATED = "simulated";

    private record CachedQuote(QuoteData quote, long at) {
    }

    private final ProviderRouter router;
    private final StockRepository stocks;
    private final PriceBarRepository bars;
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();
    private final Map<String, Object> historyLocks = new ConcurrentHashMap<>();
    private final Object stockLock = new Object();

    public MarketDataService(ProviderRouter router, StockRepository stocks, PriceBarRepository bars) {
        this.router = router;
        this.stocks = stocks;
        this.bars = bars;
    }

    // ------------------------------------------------------------------ search

    public List<SearchHit> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new StockSenseException.InvalidRequest("query is required");
        }
        String q = query.trim();
        if (q.length() > 50) {
            throw new StockSenseException.InvalidRequest("query is too long (max 50 characters)");
        }
        int max = Math.max(1, Math.min(limit, 20));
        boolean anySucceeded = false;
        String lastError = null;
        for (MarketDataProvider p : router.chain()) {
            if (!p.isAvailable()) {
                continue;
            }
            try {
                List<SearchHit> hits = p.search(q, max);
                anySucceeded = true;
                if (!hits.isEmpty()) {
                    return hits.size() > max ? hits.subList(0, max) : hits;
                }
            } catch (RuntimeException e) {
                lastError = e.getMessage();
                log.warn("search '{}' failed on {}: {}", q, p.name(), e.getMessage());
            }
        }
        if (!anySucceeded) {
            throw new StockSenseException.ProviderUnavailable("Stock search is unavailable: " + lastError);
        }
        return List.of();
    }

    // ------------------------------------------------------------------ quote

    public QuoteData quote(String rawSymbol) {
        String symbol = Symbols.normalize(rawSymbol);
        long now = System.currentTimeMillis();
        CachedQuote cached = quoteCache.get(symbol);
        if (cached != null && now - cached.at < QUOTE_TTL.toMillis()) {
            return cached.quote;
        }
        Sourced<QuoteData> result = router.call("quote", symbol, p -> p.quote(symbol));
        QuoteData q = result.value();
        quoteCache.put(symbol, new CachedQuote(q, now));
        upsertStock(symbol, s -> {
            if (s.getName() == null || s.getName().equals(symbol)) {
                s.setName(q.name());
            }
            if (s.getExchange() == null) {
                s.setExchange(q.exchange());
            }
            if (s.getCurrency() == null) {
                s.setCurrency(q.currency());
            }
        });
        return q;
    }

    // ------------------------------------------------------------------ profile

    public CompanyProfile profile(String rawSymbol) {
        String symbol = Symbols.normalize(rawSymbol);
        Stock stored = stocks.findById(symbol).orElse(null);
        // A profile without ROE/ROCE (e.g. stored before those were added) is re-fetched after an hour, not a day.
        Duration ttl = stored != null && stored.getRoe() == null && stored.getRoce() == null
                ? Duration.ofHours(1) : PROFILE_TTL;
        if (stored != null && stored.getProfileUpdatedAt() != null
                && Duration.between(stored.getProfileUpdatedAt(), Instant.now()).compareTo(ttl) < 0) {
            return toProfile(stored);
        }
        try {
            CompanyProfile cp = router.call("profile", symbol, p -> p.profile(symbol)).value();
            upsertStock(symbol, s -> {
                s.setName(cp.name() != null ? cp.name() : s.getName());
                if (cp.sector() != null) s.setSector(cp.sector());
                if (cp.industry() != null) s.setIndustry(cp.industry());
                if (cp.exchange() != null) s.setExchange(cp.exchange());
                if (cp.currency() != null) s.setCurrency(cp.currency());
                if (cp.country() != null) s.setCountry(cp.country());
                if (cp.website() != null) s.setWebsite(cp.website());
                if (cp.description() != null) s.setDescription(cp.description());
                if (cp.marketCap() != null) s.setMarketCap(cp.marketCap());
                if (cp.peRatio() != null) s.setPeRatio(cp.peRatio());
                if (cp.eps() != null) s.setEps(cp.eps());
                if (cp.dividendYield() != null) s.setDividendYield(cp.dividendYield());
                if (cp.beta() != null) s.setBeta(cp.beta());
                if (cp.week52High() != null) s.setWeek52High(cp.week52High());
                if (cp.week52Low() != null) s.setWeek52Low(cp.week52Low());
                if (cp.roe() != null) s.setRoe(cp.roe());
                if (cp.roce() != null) s.setRoce(cp.roce());
                s.setProfileUpdatedAt(Instant.now());
            });
            return cp;
        } catch (StockSenseException.ProviderUnavailable e) {
            if (stored != null && stored.getName() != null) {
                return toProfile(stored);
            }
            throw e;
        }
    }

    public String displayName(String symbol) {
        String n = stocks.findById(symbol).map(Stock::getName).orElse(null);
        if (n == null || n.equals(symbol)) {
            try {
                n = quote(symbol).name();
            } catch (RuntimeException e) {
                n = null;
            }
        }
        return n == null ? symbol : n;
    }

    // ------------------------------------------------------------------ history

    /** All stored daily bars (oldest first), refreshed from the providers when stale. */
    public List<Bar> history(String rawSymbol) {
        String symbol = Symbols.normalize(rawSymbol);
        synchronized (historyLocks.computeIfAbsent(symbol, k -> new Object())) {
            Stock stock = stocks.findById(symbol).orElse(null);
            long count = bars.countBySymbol(symbol);
            boolean stale = stock == null || stock.getHistoryUpdatedAt() == null || count < 30
                    || Duration.between(stock.getHistoryUpdatedAt(), Instant.now()).compareTo(HISTORY_TTL) > 0;
            if (stale) {
                try {
                    refreshHistory(symbol, stock, count);
                } catch (StockSenseException e) {
                    if (count == 0) {
                        throw e;
                    }
                    log.warn("History refresh failed for {}, serving stored data: {}", symbol, e.getMessage());
                }
            }
        }
        List<Bar> out = new ArrayList<>();
        for (PriceBar b : bars.findBySymbolOrderByTradeDateAsc(symbol)) {
            out.add(new Bar(b.getTradeDate(), b.getOpenPrice(), b.getHighPrice(), b.getLowPrice(), b.getClosePrice(), b.getVolume()));
        }
        if (out.isEmpty()) {
            throw new StockSenseException.SymbolNotFound(symbol);
        }
        return out;
    }

    /** Bars from the last {@code days} calendar days (relative to the latest bar). */
    public List<Bar> history(String symbol, int days) {
        List<Bar> all = history(symbol);
        LocalDate cutoff = all.get(all.size() - 1).date().minusDays(days);
        List<Bar> out = new ArrayList<>();
        for (Bar b : all) {
            if (!b.date().isBefore(cutoff)) {
                out.add(b);
            }
        }
        return out;
    }

    public String dataSource(String symbol) {
        return stocks.findById(symbol).map(Stock::getDataSource).orElse("unknown");
    }

    private void refreshHistory(String symbol, Stock stock, long count) {
        Sourced<List<Bar>> result = router.call("history", symbol, p -> p.history(symbol, STORE_DAYS));
        String source = result.source();
        List<Bar> fetched = result.value();
        String oldSource = stock == null ? null : stock.getDataSource();

        if (count > 0 && oldSource != null && !SIMULATED.equals(oldSource) && SIMULATED.equals(source)) {
            log.warn("Real providers are down for {}; keeping stored {} data instead of simulated data", symbol, oldSource);
            upsertStock(symbol, s -> s.setHistoryUpdatedAt(Instant.now().minus(HISTORY_TTL).plusSeconds(900)));
            return;
        }
        if (count > 0 && SIMULATED.equals(oldSource) && !SIMULATED.equals(source)) {
            bars.deleteAllBySymbol(symbol); // replace fake data with the real thing
        }

        LocalDate from = fetched.get(0).date();
        Map<LocalDate, PriceBar> existing = new HashMap<>();
        for (PriceBar b : bars.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(symbol, from)) {
            existing.put(b.getTradeDate(), b);
        }
        List<PriceBar> toSave = new ArrayList<>();
        for (Bar b : fetched) {
            PriceBar e = existing.get(b.date());
            if (e == null) {
                toSave.add(new PriceBar(symbol, b.date(), b.open(), b.high(), b.low(), b.close(), b.volume()));
            } else if (e.getClosePrice() != b.close() || e.getHighPrice() != b.high() || e.getLowPrice() != b.low()
                    || e.getOpenPrice() != b.open() || e.getVolume() != b.volume()) {
                e.update(b.open(), b.high(), b.low(), b.close(), b.volume());
                toSave.add(e);
            }
        }
        bars.saveAll(toSave);
        upsertStock(symbol, s -> {
            s.setDataSource(source);
            s.setHistoryUpdatedAt(Instant.now());
        });
        log.info("Stored {} new/updated bars for {} from {}", toSave.size(), symbol, source);
    }

    // ------------------------------------------------------------------ helpers

    private void upsertStock(String symbol, Consumer<Stock> change) {
        synchronized (stockLock) {
            Stock s = stocks.findById(symbol).orElseGet(() -> new Stock(symbol));
            change.accept(s);
            stocks.save(s);
        }
    }

    private static CompanyProfile toProfile(Stock s) {
        return new CompanyProfile(s.getSymbol(), s.getName() == null ? s.getSymbol() : s.getName(), s.getSector(),
                s.getIndustry(), s.getExchange(), s.getCurrency(), s.getCountry(), s.getWebsite(), s.getDescription(),
                s.getMarketCap(), s.getPeRatio(), s.getEps(), s.getDividendYield(), s.getBeta(), s.getWeek52High(),
                s.getWeek52Low(), s.getRoe(), s.getRoce(), s.getDataSource() == null ? "cache" : s.getDataSource());
    }
}
