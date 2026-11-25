package com.stocksense.service;

import com.stocksense.domain.Prediction;
import com.stocksense.domain.PredictionStatus;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.repository.PredictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scores stored predictions against what actually happened: the label of a prediction made on day t with
 * horizon h is whether close[t+h] (h trading days later) is above close[t].
 */
@Service
public class PredictionEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PredictionEvaluationService.class);

    private final PredictionRepository predictions;
    private final MarketDataService market;

    public PredictionEvaluationService(PredictionRepository predictions, MarketDataService market) {
        this.predictions = predictions;
        this.market = market;
    }

    @Scheduled(cron = "${stocksense.evaluation.cron:0 30 18 * * *}")
    public void evaluateAllPending() {
        for (String symbol : predictions.findPendingSymbols()) {
            evaluateSymbolQuietly(symbol);
        }
    }

    public void evaluateSymbolQuietly(String symbol) {
        try {
            int n = evaluateSymbol(symbol);
            if (n > 0) {
                log.info("Evaluated {} prediction(s) for {}", n, symbol);
            }
        } catch (RuntimeException e) {
            log.warn("Could not evaluate predictions for {}: {}", symbol, e.getMessage());
        }
    }

    public int evaluateSymbol(String symbol) {
        List<Prediction> pending = predictions.findBySymbolAndStatus(symbol, PredictionStatus.PENDING);
        if (pending.isEmpty()) {
            return 0;
        }
        List<Bar> bars = market.history(symbol);
        int done = 0;
        for (Prediction p : pending) {
            int base = indexOf(bars, p);
            int target = base + p.getHorizonDays();
            if (base >= 0 && target < bars.size()) {
                p.markEvaluated(bars.get(target).close());
                predictions.save(p);
                done++;
            }
        }
        return done;
    }

    private static int indexOf(List<Bar> bars, Prediction p) {
        for (int i = 0; i < bars.size(); i++) {
            if (bars.get(i).date().equals(p.getBaseDate())) {
                return i;
            }
        }
        return -1;
    }
}
