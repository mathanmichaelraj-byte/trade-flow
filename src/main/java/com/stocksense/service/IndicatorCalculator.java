package com.stocksense.service;

import com.stocksense.market.MarketModels.Bar;

import java.util.Arrays;
import java.util.List;

/** Pure technical-indicator math. Arrays are aligned with the input bars; undefined values are NaN. */
public final class IndicatorCalculator {

    private IndicatorCalculator() {
    }

    public record Indicators(double[] sma20, double[] sma50, double[] sma200, double[] ema12, double[] ema20,
                             double[] ema26, double[] rsi14, double[] macd, double[] macdSignal, double[] macdHist,
                             double[] bbMiddle, double[] bbUpper, double[] bbLower, double[] atr14) {
    }

    public static Indicators compute(List<Bar> bars) {
        int n = bars.size();
        double[] close = new double[n];
        double[] high = new double[n];
        double[] low = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = bars.get(i).close();
            high[i] = bars.get(i).high();
            low[i] = bars.get(i).low();
        }
        double[] ema12 = ema(close, 12);
        double[] ema26 = ema(close, 26);
        double[] macd = new double[n];
        for (int i = 0; i < n; i++) {
            macd[i] = Double.isNaN(ema12[i]) || Double.isNaN(ema26[i]) ? Double.NaN : ema12[i] - ema26[i];
        }
        double[] signal = ema(macd, 9);
        double[] hist = new double[n];
        for (int i = 0; i < n; i++) {
            hist[i] = Double.isNaN(macd[i]) || Double.isNaN(signal[i]) ? Double.NaN : macd[i] - signal[i];
        }
        double[] mid = sma(close, 20);
        double[] upper = nan(n);
        double[] lower = nan(n);
        for (int i = 19; i < n; i++) {
            double sum = 0;
            for (int j = i - 19; j <= i; j++) {
                double d = close[j] - mid[i];
                sum += d * d;
            }
            double sd = Math.sqrt(sum / 20.0);
            upper[i] = mid[i] + 2 * sd;
            lower[i] = mid[i] - 2 * sd;
        }
        return new Indicators(sma(close, 20), sma(close, 50), sma(close, 200), ema12, ema(close, 20), ema26,
                rsi(close, 14), macd, signal, hist, mid, upper, lower, atr(high, low, close, 14));
    }

    public static double[] sma(double[] v, int n) {
        double[] out = nan(v.length);
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            sum += v[i];
            if (i >= n) {
                sum -= v[i - n];
            }
            if (i >= n - 1) {
                out[i] = sum / n;
            }
        }
        return out;
    }

    /** EMA seeded with the SMA of the first n valid values; leading NaNs are skipped. */
    public static double[] ema(double[] v, int n) {
        double[] out = nan(v.length);
        int s = 0;
        while (s < v.length && Double.isNaN(v[s])) {
            s++;
        }
        if (v.length - s < n) {
            return out;
        }
        double alpha = 2.0 / (n + 1);
        double seed = 0;
        for (int i = s; i < s + n; i++) {
            seed += v[i];
        }
        out[s + n - 1] = seed / n;
        for (int i = s + n; i < v.length; i++) {
            out[i] = v[i] * alpha + out[i - 1] * (1 - alpha);
        }
        return out;
    }

    /** Wilder's RSI. */
    public static double[] rsi(double[] c, int n) {
        double[] out = nan(c.length);
        if (c.length <= n) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= n; i++) {
            double d = c[i] - c[i - 1];
            gain += Math.max(d, 0);
            loss += Math.max(-d, 0);
        }
        gain /= n;
        loss /= n;
        out[n] = rs(gain, loss);
        for (int i = n + 1; i < c.length; i++) {
            double d = c[i] - c[i - 1];
            gain = (gain * (n - 1) + Math.max(d, 0)) / n;
            loss = (loss * (n - 1) + Math.max(-d, 0)) / n;
            out[i] = rs(gain, loss);
        }
        return out;
    }

    private static double rs(double gain, double loss) {
        if (loss == 0) {
            return gain == 0 ? 50.0 : 100.0;
        }
        return 100.0 - 100.0 / (1.0 + gain / loss);
    }

    /** Wilder's Average True Range. */
    public static double[] atr(double[] high, double[] low, double[] close, int n) {
        int len = close.length;
        double[] out = nan(len);
        if (len < n) {
            return out;
        }
        double[] tr = new double[len];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < len; i++) {
            tr[i] = Math.max(high[i] - low[i], Math.max(Math.abs(high[i] - close[i - 1]), Math.abs(low[i] - close[i - 1])));
        }
        double seed = 0;
        for (int i = 0; i < n; i++) {
            seed += tr[i];
        }
        out[n - 1] = seed / n;
        for (int i = n; i < len; i++) {
            out[i] = (out[i - 1] * (n - 1) + tr[i]) / n;
        }
        return out;
    }

    private static double[] nan(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }
}
