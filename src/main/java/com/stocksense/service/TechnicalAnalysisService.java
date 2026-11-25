package com.stocksense.service;

import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.service.IndicatorCalculator.Indicators;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Indicator series for charts plus rule-based BUY/SELL/NEUTRAL signals for the latest bar. */
@Service
public class TechnicalAnalysisService {

    public record Point(LocalDate date, double close, Double sma20, Double sma50, Double ema20, Double rsi14,
                        Double macd, Double macdSignal, Double macdHistogram, Double bbUpper, Double bbMiddle,
                        Double bbLower, Double atr14) {
    }

    public record Signal(String indicator, Double value, String signal, String note) {
    }

    public record Result(String symbol, LocalDate asOf, double price, String overall, int buy, int sell,
                         int neutral, List<Signal> signals, List<Point> series) {
    }

    private final MarketDataService market;

    public TechnicalAnalysisService(MarketDataService market) {
        this.market = market;
    }

    public Result analyze(String symbol, int days) {
        List<Bar> bars = market.history(symbol);
        if (bars.size() < 35) {
            throw new StockSenseException.InsufficientData("At least 35 daily bars are needed for technical analysis (have " + bars.size() + ")");
        }
        Indicators ind = IndicatorCalculator.compute(bars);
        int last = bars.size() - 1;
        double price = bars.get(last).close();

        List<Signal> signals = new ArrayList<>();
        double rsi = ind.rsi14()[last];
        if (!Double.isNaN(rsi)) {
            if (rsi < 30) signals.add(new Signal("RSI(14)", rsi, "BUY", "Oversold (below 30)"));
            else if (rsi > 70) signals.add(new Signal("RSI(14)", rsi, "SELL", "Overbought (above 70)"));
            else signals.add(new Signal("RSI(14)", rsi, "NEUTRAL", "Momentum is neither overbought nor oversold"));
        }
        double macd = ind.macd()[last];
        double sig = ind.macdSignal()[last];
        double hist = ind.macdHist()[last];
        if (!Double.isNaN(hist)) {
            if (macd > sig && hist > 0) signals.add(new Signal("MACD", hist, "BUY", "MACD is above its signal line"));
            else if (macd < sig && hist < 0) signals.add(new Signal("MACD", hist, "SELL", "MACD is below its signal line"));
            else signals.add(new Signal("MACD", hist, "NEUTRAL", "MACD and signal line are converging"));
        }
        double sma20 = ind.sma20()[last];
        double sma50 = ind.sma50()[last];
        if (!Double.isNaN(sma50)) {
            if (price > sma50 && sma20 > sma50) signals.add(new Signal("SMA 20/50 trend", sma50, "BUY", "Price and SMA20 are above SMA50 (uptrend)"));
            else if (price < sma50 && sma20 < sma50) signals.add(new Signal("SMA 20/50 trend", sma50, "SELL", "Price and SMA20 are below SMA50 (downtrend)"));
            else signals.add(new Signal("SMA 20/50 trend", sma50, "NEUTRAL", "Mixed trend"));
        }
        double sma200 = ind.sma200()[last];
        if (!Double.isNaN(sma200) && !Double.isNaN(sma50)) {
            if (sma50 > sma200) signals.add(new Signal("SMA 50/200", sma200, "BUY", "SMA50 above SMA200 (golden-cross regime)"));
            else signals.add(new Signal("SMA 50/200", sma200, "SELL", "SMA50 below SMA200 (death-cross regime)"));
        }
        double upper = ind.bbUpper()[last];
        double lower = ind.bbLower()[last];
        if (!Double.isNaN(upper) && upper > lower) {
            double pctB = (price - lower) / (upper - lower);
            if (price <= lower) signals.add(new Signal("Bollinger %B", pctB, "BUY", "Price at or below the lower band"));
            else if (price >= upper) signals.add(new Signal("Bollinger %B", pctB, "SELL", "Price at or above the upper band"));
            else signals.add(new Signal("Bollinger %B", pctB, "NEUTRAL", "Price is inside the bands"));
        }
        double atr = ind.atr14()[last];
        if (!Double.isNaN(atr)) {
            double pct = atr / price * 100.0;
            String level = pct > 3 ? "High" : pct > 1.5 ? "Moderate" : "Low";
            signals.add(new Signal("ATR(14) % of price", pct, "NEUTRAL", level + " volatility"));
        }

        int buy = 0;
        int sell = 0;
        int neutral = 0;
        for (Signal s : signals) {
            switch (s.signal()) {
                case "BUY" -> buy++;
                case "SELL" -> sell++;
                default -> neutral++;
            }
        }
        String overall = buy - sell >= 2 ? "BULLISH" : sell - buy >= 2 ? "BEARISH" : "NEUTRAL";

        LocalDate cutoff = bars.get(last).date().minusDays(days);
        List<Point> series = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            if (b.date().isBefore(cutoff)) {
                continue;
            }
            series.add(new Point(b.date(), b.close(), v(ind.sma20(), i), v(ind.sma50(), i), v(ind.ema20(), i),
                    v(ind.rsi14(), i), v(ind.macd(), i), v(ind.macdSignal(), i), v(ind.macdHist(), i),
                    v(ind.bbUpper(), i), v(ind.bbMiddle(), i), v(ind.bbLower(), i), v(ind.atr14(), i)));
        }
        return new Result(symbol, bars.get(last).date(), price, overall, buy, sell, neutral, signals, series);
    }

    private static Double v(double[] a, int i) {
        return Double.isNaN(a[i]) ? null : a[i];
    }
}
