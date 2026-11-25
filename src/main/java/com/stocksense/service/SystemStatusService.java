package com.stocksense.service;

import com.stocksense.market.MarketDataProvider;
import com.stocksense.market.ProviderRouter;
import com.stocksense.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SystemStatusService {

    public record ProviderStatus(String name, boolean available) {
    }

    public record Status(String status, Instant serverTime, String database, List<ProviderStatus> providers,
                         String mlStatus, String mlDetail, String version) {
    }

    private final ProviderRouter router;
    private final StockRepository stocks;
    private final MlClient ml;

    public SystemStatusService(ProviderRouter router, StockRepository stocks, MlClient ml) {
        this.router = router;
        this.stocks = stocks;
        this.ml = ml;
    }

    public Status status() {
        String db;
        try {
            stocks.count();
            db = "UP";
        } catch (RuntimeException e) {
            db = "DOWN";
        }
        String mlStatus;
        String mlDetail;
        try {
            mlDetail = ml.health();
            mlStatus = "UP";
        } catch (RuntimeException e) {
            mlStatus = "DOWN";
            mlDetail = "ML service is not reachable";
        }
        List<ProviderStatus> providers = router.chain().stream()
                .map((MarketDataProvider p) -> new ProviderStatus(p.name(), p.isAvailable())).toList();
        String overall = "UP".equals(db) ? ("UP".equals(mlStatus) ? "UP" : "DEGRADED") : "DOWN";
        return new Status(overall, Instant.now(), db, providers, mlStatus, mlDetail, "1.0.0");
    }
}
