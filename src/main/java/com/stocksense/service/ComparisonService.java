package com.stocksense.service;

import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.service.HistoryAnalysisService.Summary;
import com.stocksense.service.TechnicalAnalysisService.Signal;
import com.stocksense.util.Symbols;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Side-by-side comparison of 2-5 stocks; price series are rebased to 100 at the start of the range. */
@Service
public class ComparisonService {

    public record SeriesPoint(LocalDate date, double value) {
    }

    public record Item(String symbol, String name, double price, double periodReturnPercent,
                       double annualizedVolatility, double maxDrawdownPercent, Double rsi14, String overall,
                       List<SeriesPoint> series) {
    }

    public record Result(String range, List<Item> items) {
    }

    private final MarketDataService market;
    private final TechnicalAnalysisService technical;

    public ComparisonService(MarketDataService market, TechnicalAnalysisService technical) {
        this.market = market;
        this.technical = technical;
    }

    public Result compare(List<String> rawSymbols, String range) {
        Set<String> symbols = new LinkedHashSet<>();
        for (String s : rawSymbols) {
            symbols.add(Symbols.normalize(s));
        }
        if (symbols.size() < 2 || symbols.size() > 5) {
            throw new StockSenseException.InvalidRequest("Provide between 2 and 5 distinct symbols");
        }
        String r = range == null || range.isBlank() ? "1Y" : range.trim().toUpperCase();
        int days = HistoryAnalysisService.rangeDays(r);
        List<Item> items = new ArrayList<>();
        for (String symbol : symbols) {
            List<Bar> bars = market.history(symbol, days);
            Summary s = HistoryAnalysisService.summarize(bars);
            TechnicalAnalysisService.Result t = technical.analyze(symbol, 30);
            Double rsi = null;
            for (Signal sig : t.signals()) {
                if (sig.indicator().startsWith("RSI")) {
                    rsi = sig.value();
                }
            }
            double base = bars.get(0).close();
            List<SeriesPoint> series = new ArrayList<>();
            for (Bar b : bars) {
                series.add(new SeriesPoint(b.date(), b.close() / base * 100.0));
            }
            items.add(new Item(symbol, market.displayName(symbol), s.endPrice(), s.changePercent(),
                    s.annualizedVolatility(), s.maxDrawdownPercent(), rsi, t.overall(), series));
        }
        return new Result(r, items);
    }
}
