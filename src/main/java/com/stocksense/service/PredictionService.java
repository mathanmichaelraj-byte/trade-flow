package com.stocksense.service;

import com.stocksense.domain.ModelMetric;
import com.stocksense.domain.Prediction;
import com.stocksense.domain.PredictionStatus;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.repository.ModelMetricRepository;
import com.stocksense.repository.PredictionRepository;
import com.stocksense.service.MlClient.Driver;
import com.stocksense.service.MlClient.ModelScore;
import com.stocksense.util.Symbols;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Orchestrates ML predictions: data -> Python service -> persistence -> evaluation records. */
@Service
public class PredictionService {

    public static final Set<Integer> HORIZONS = Set.of(1, 5, 10, 30);
    private static final int MIN_BARS = 250;

    public record PredictionView(Prediction prediction, boolean cached, List<ModelMetric> models, List<Driver> drivers) {
    }

    public record Tally(long total, long evaluated, long correct) {
        public double hitRatePercent() {
            return evaluated == 0 ? 0 : correct * 100.0 / evaluated;
        }
    }

    public record Performance(List<ModelMetric> metrics, Tally live) {
    }

    private final MarketDataService market;
    private final MlClient ml;
    private final PredictionRepository predictions;
    private final ModelMetricRepository metrics;
    private final PredictionEvaluationService evaluation;

    public PredictionService(MarketDataService market, MlClient ml, PredictionRepository predictions,
                             ModelMetricRepository metrics, PredictionEvaluationService evaluation) {
        this.market = market;
        this.ml = ml;
        this.predictions = predictions;
        this.metrics = metrics;
        this.evaluation = evaluation;
    }

    public PredictionView predict(String rawSymbol, Integer rawHorizon, boolean forceRetrain) {
        String symbol = Symbols.normalize(rawSymbol);
        int horizon = rawHorizon == null ? 5 : rawHorizon;
        if (!HORIZONS.contains(horizon)) {
            throw new StockSenseException.InvalidRequest("horizonDays must be one of 1, 5, 10, 30 (trading days)");
        }
        List<Bar> bars = market.history(symbol);
        if (bars.size() < MIN_BARS) {
            throw new StockSenseException.InsufficientData("At least " + MIN_BARS + " daily bars are needed to train a model (have " + bars.size() + ")");
        }
        Bar last = bars.get(bars.size() - 1);

        Optional<Prediction> existing = predictions.findFirstBySymbolAndHorizonDaysAndBaseDate(symbol, horizon, last.date());
        if (existing.isPresent() && !forceRetrain) {
            return view(existing.get(), true);
        }

        MlClient.Result r = ml.predict(symbol, horizon, bars, forceRetrain);
        saveMetrics(symbol, horizon, r);

        existing.ifPresent(predictions::delete);
        Prediction p = new Prediction(symbol, horizon, r.direction(), r.probabilityUp(), r.confidence(), r.modelName(),
                r.modelVersion(), last.date(), last.close(), targetDate(last.date(), horizon));
        p.setTrainSamples(r.trainSamples());
        p.setTestSamples(r.testSamples());
        p.setBaselineAccuracy(r.baselineAccuracy());
        p.setReliability(r.reliability());
        p.setDrivers(encodeDrivers(r.drivers()));
        return view(predictions.save(p), false);
    }

    /** Prediction history; scores any predictions whose horizon has passed first. */
    public List<Prediction> history(String rawSymbol, int limit) {
        int max = Math.max(1, Math.min(limit, 200));
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return predictions.findAllByOrderByCreatedAtDesc(PageRequest.of(0, max));
        }
        String symbol = Symbols.normalize(rawSymbol);
        evaluation.evaluateSymbolQuietly(symbol);
        return predictions.findBySymbolOrderByCreatedAtDesc(symbol, PageRequest.of(0, max));
    }

    public Tally tally(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return new Tally(predictions.count(), predictions.countByStatus(PredictionStatus.EVALUATED),
                    predictions.countByStatusAndCorrectTrue(PredictionStatus.EVALUATED));
        }
        String symbol = Symbols.normalize(rawSymbol);
        return new Tally(predictions.countBySymbol(symbol),
                predictions.countBySymbolAndStatus(symbol, PredictionStatus.EVALUATED),
                predictions.countBySymbolAndStatusAndCorrectTrue(symbol, PredictionStatus.EVALUATED));
    }

    public Performance performance(String rawSymbol, Integer horizon) {
        PageRequest page = PageRequest.of(0, 60);
        List<ModelMetric> rows = rawSymbol == null || rawSymbol.isBlank()
                ? metrics.findAllByOrderByTrainedAtDesc(page)
                : metrics.findBySymbolOrderByTrainedAtDesc(Symbols.normalize(rawSymbol), page);
        if (horizon != null) {
            rows = rows.stream().filter(m -> m.getHorizonDays() == horizon).toList();
        }
        return new Performance(rows, tally(rawSymbol));
    }

    public PredictionView view(Prediction p, boolean cached) {
        List<ModelMetric> models = metrics.findBySymbolAndHorizonDaysAndModelVersionOrderByAccuracyDesc(
                p.getSymbol(), p.getHorizonDays(), p.getModelVersion());
        return new PredictionView(p, cached, models, decodeDrivers(p.getDrivers()));
    }

    // ------------------------------------------------------------------

    private void saveMetrics(String symbol, int horizon, MlClient.Result r) {
        if (metrics.existsBySymbolAndHorizonDaysAndModelVersion(symbol, horizon, r.modelVersion())) {
            return;
        }
        List<ModelMetric> rows = new ArrayList<>();
        for (ModelScore m : r.models()) {
            rows.add(new ModelMetric(symbol, horizon, m.name(), r.modelVersion(), m.accuracy(), m.precision(),
                    m.recall(), m.f1(), m.rocAuc(), r.trainSamples(), r.testSamples(), r.baselineAccuracy(),
                    m.selected(), r.trainedAt()));
        }
        metrics.saveAll(rows);
    }

    /** n-th business day after the base date (weekends skipped; exchange holidays are not modelled). */
    static LocalDate targetDate(LocalDate base, int horizon) {
        LocalDate d = base;
        int remaining = horizon;
        while (remaining > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return d;
    }

    private static String encodeDrivers(List<Driver> drivers) {
        StringBuilder sb = new StringBuilder();
        for (Driver d : drivers) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(d.name().replace(";", "").replace("=", "")).append('=').append(String.format(java.util.Locale.ROOT, "%.4f", d.importance()));
        }
        return sb.toString();
    }

    private static List<Driver> decodeDrivers(String s) {
        List<Driver> out = new ArrayList<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(";")) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                try {
                    out.add(new Driver(kv[0], Double.parseDouble(kv[1])));
                } catch (NumberFormatException ignored) {
                    // skip malformed entry
                }
            }
        }
        return out;
    }
}
