package com.stocksense.market;

import com.stocksense.config.AppProperties;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Sourced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Tries the configured providers in order and returns the first successful answer. */
@Component
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final List<MarketDataProvider> chain = new ArrayList<>();

    public ProviderRouter(List<MarketDataProvider> all, AppProperties props) {
        for (String wanted : props.market().providers()) {
            String n = wanted.trim().toLowerCase(Locale.ROOT);
            for (MarketDataProvider p : all) {
                if (p.name().equals(n) && !chain.contains(p)) {
                    chain.add(p);
                }
            }
        }
        if (chain.isEmpty()) {
            throw new IllegalStateException("No market data provider configured: " + props.market().providers());
        }
        log.info("Market data provider chain: {}", chain.stream().map(MarketDataProvider::name).toList());
    }

    public List<MarketDataProvider> chain() {
        return List.copyOf(chain);
    }

    public <T> Sourced<T> call(String operation, String subject, Function<MarketDataProvider, T> fn) {
        RuntimeException last = null;
        boolean onlyNotFound = true;
        int tried = 0;
        for (MarketDataProvider p : chain) {
            if (!p.isAvailable()) {
                continue;
            }
            tried++;
            try {
                T value = fn.apply(p);
                if (value != null) {
                    return new Sourced<>(value, p.name());
                }
            } catch (StockSenseException.SymbolNotFound e) {
                last = e;
            } catch (RuntimeException e) {
                onlyNotFound = false;
                last = e;
                log.warn("{} '{}' failed on {}: {}", operation, subject, p.name(), e.getMessage());
            }
        }
        if (tried > 0 && onlyNotFound && last instanceof StockSenseException.SymbolNotFound nf) {
            throw nf;
        }
        throw new StockSenseException.ProviderUnavailable("No market data provider could serve " + operation
                + " for '" + subject + "'" + (last != null ? ": " + last.getMessage() : ""));
    }
}
