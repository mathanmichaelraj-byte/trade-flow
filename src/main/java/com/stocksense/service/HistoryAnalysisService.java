package com.stocksense.service;

import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Historical performance statistics and daily/weekly/monthly/yearly aggregation. */
@Service
public class HistoryAnalysisService {

    public record Summary(LocalDate startDate, LocalDate endDate, double startPrice, double endPrice,
                          double changePercent, double high, double low, double averageVolume,
                          double annualizedVolatility, double maxDrawdownPercent, double bestDayPercent,
                          double worstDayPercent, int upDays, int downDays) {
    }

    public record Result(String symbol, String range, String interval, String source, List<Bar> bars, Summary summary) {
    }

    private final MarketDataService market;

    public HistoryAnalysisService(MarketDataService market) {
        this.market = market;
    }

    public Result analyze(String symbol, String range, String interval) {
        String r = range == null || range.isBlank() ? "1Y" : range.trim().toUpperCase(Locale.ROOT);
        String iv = interval == null || interval.isBlank() ? "DAILY" : interval.trim().toUpperCase(Locale.ROOT);
        List<Bar> daily = market.history(symbol, rangeDays(r));
        List<Bar> shown = aggregate(daily, iv);
        return new Result(symbol, r, iv, market.dataSource(symbol), shown, summarize(daily));
    }

    public static int rangeDays(String range) {
        return switch (range == null ? "1Y" : range.toUpperCase(Locale.ROOT)) {
            case "1W" -> 7;
            case "1M" -> 30;
            case "3M" -> 91;
            case "6M" -> 182;
            case "1Y" -> 365;
            case "3Y" -> 1095;
            default -> throw new StockSenseException.InvalidRequest("range must be one of 1W, 1M, 3M, 6M, 1Y, 3Y");
        };
    }

    public static List<Bar> aggregate(List<Bar> daily, String interval) {
        if ("DAILY".equals(interval)) {
            return daily;
        }
        Map<Object, List<Bar>> groups = new LinkedHashMap<>();
        for (Bar b : daily) {
            Object key = switch (interval) {
                case "WEEKLY" -> b.date().with(DayOfWeek.MONDAY);
                case "MONTHLY" -> YearMonth.from(b.date());
                case "YEARLY" -> b.date().getYear();
                default -> throw new StockSenseException.InvalidRequest("interval must be one of DAILY, WEEKLY, MONTHLY, YEARLY");
            };
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        List<Bar> out = new ArrayList<>();
        for (List<Bar> g : groups.values()) {
            double high = Double.MIN_VALUE;
            double low = Double.MAX_VALUE;
            long volume = 0;
            for (Bar b : g) {
                high = Math.max(high, b.high());
                low = Math.min(low, b.low());
                volume += b.volume();
            }
            out.add(new Bar(g.get(g.size() - 1).date(), g.get(0).open(), high, low, g.get(g.size() - 1).close(), volume));
        }
        return out;
    }

    public static Summary summarize(List<Bar> bars) {
        if (bars.isEmpty()) {
            throw new StockSenseException.InsufficientData("No price history available");
        }
        Bar first = bars.get(0);
        Bar last = bars.get(bars.size() - 1);
        double high = 0;
        double low = Double.MAX_VALUE;
        double volSum = 0;
        double peak = first.close();
        double maxDd = 0;
        double best = Double.NEGATIVE_INFINITY;
        double worst = Double.POSITIVE_INFINITY;
        int up = 0;
        int down = 0;
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            high = Math.max(high, b.high());
            low = Math.min(low, b.low());
            volSum += b.volume();
            peak = Math.max(peak, b.close());
            maxDd = Math.max(maxDd, (peak - b.close()) / peak);
            if (i > 0) {
                double r = b.close() / bars.get(i - 1).close() - 1.0;
                returns.add(r);
                best = Math.max(best, r);
                worst = Math.min(worst, r);
                if (r > 0) up++;
                else if (r < 0) down++;
            }
        }
        double vol = 0;
        if (returns.size() > 1) {
            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (returns.size() - 1);
            vol = Math.sqrt(var) * Math.sqrt(252) * 100.0;
        }
        return new Summary(first.date(), last.date(), first.close(), last.close(),
                (last.close() / first.close() - 1.0) * 100.0, high, low, volSum / bars.size(), vol, maxDd * 100.0,
                returns.isEmpty() ? 0 : best * 100.0, returns.isEmpty() ? 0 : worst * 100.0, up, down);
    }
}
