package com.stocksense;

import com.stocksense.service.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorCalculatorTest {

    @Test
    void smaMatchesHandComputation() {
        double[] out = IndicatorCalculator.sma(new double[]{1, 2, 3, 4, 5}, 3);
        assertTrue(Double.isNaN(out[0]) && Double.isNaN(out[1]));
        assertEquals(2.0, out[2], 1e-9);
        assertEquals(3.0, out[3], 1e-9);
        assertEquals(4.0, out[4], 1e-9);
    }

    @Test
    void emaIsSeededWithSmaThenSmoothed() {
        double[] out = IndicatorCalculator.ema(new double[]{1, 2, 3, 4, 5}, 3);
        assertEquals(2.0, out[2], 1e-9);          // SMA seed
        assertEquals(3.0, out[3], 1e-9);          // 4*0.5 + 2*0.5
        assertEquals(4.0, out[4], 1e-9);          // 5*0.5 + 3*0.5
    }

    @Test
    void rsiIs100ForMonotonicallyRisingSeries() {
        double[] c = new double[30];
        for (int i = 0; i < c.length; i++) {
            c[i] = 100 + i;
        }
        double[] rsi = IndicatorCalculator.rsi(c, 14);
        assertTrue(Double.isNaN(rsi[13]));
        assertEquals(100.0, rsi[29], 1e-9);
    }

    @Test
    void rsiIs50ForFlatSeriesAndBelow50WhenFalling() {
        double[] flat = new double[20];
        java.util.Arrays.fill(flat, 10);
        assertEquals(50.0, IndicatorCalculator.rsi(flat, 14)[19], 1e-9);
        double[] falling = new double[30];
        for (int i = 0; i < falling.length; i++) {
            falling[i] = 100 - i;
        }
        assertEquals(0.0, IndicatorCalculator.rsi(falling, 14)[29], 1e-9);
    }

    @Test
    void atrOfConstantRangeEqualsRange() {
        int n = 30;
        double[] high = new double[n];
        double[] low = new double[n];
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            high[i] = 102;
            low[i] = 98;
            close[i] = 100;
        }
        assertEquals(4.0, IndicatorCalculator.atr(high, low, close, 14)[n - 1], 1e-9);
    }
}
