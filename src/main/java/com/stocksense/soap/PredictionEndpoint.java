package com.stocksense.soap;

import com.stocksense.domain.Prediction;
import com.stocksense.service.PredictionService;
import com.stocksense.service.PredictionService.PredictionView;
import com.stocksense.util.Symbols;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

import static com.stocksense.soap.SoapXml.add;
import static com.stocksense.soap.SoapXml.boolValue;
import static com.stocksense.soap.SoapXml.element;
import static com.stocksense.soap.SoapXml.group;
import static com.stocksense.soap.SoapXml.intValue;
import static com.stocksense.soap.SoapXml.list;
import static com.stocksense.soap.SoapXml.text;

/** Machine Learning Prediction Module: predictStock, getPredictionHistory, getModelPerformance. */
@Component
public class PredictionEndpoint implements WsOperationHandler {

    static final String DISCLAIMER = "Educational and analytical use only. Predictions are probabilistic estimates "
            + "and are not guaranteed outcomes or financial advice.";

    private final PredictionService predictions;

    public PredictionEndpoint(PredictionService predictions) {
        this.predictions = predictions;
    }

    @Override
    public Set<String> operations() {
        return Set.of("predictStock", "getPredictionHistory", "getModelPerformance");
    }

    @Override
    public Element handle(String operation, Element request, Document out) {
        Element response = element(out, operation + "Response");
        switch (operation) {
            case "predictStock" -> {
                PredictionView v = predictions.predict(text(request, "symbol"), intValue(request, "horizonDays"),
                        boolValue(request, "forceRetrain"));
                Element pe = group(response, "prediction");
                fill(pe, v.prediction());
                add(pe, "cached", v.cached());
                list(pe, "models", "model", v.models(), (e, m) -> {
                    add(e, "name", m.getModelName());
                    add(e, "accuracy", m.getAccuracy());
                    add(e, "precision", m.getPrecisionScore());
                    add(e, "recall", m.getRecallScore());
                    add(e, "f1", m.getF1Score());
                    add(e, "rocAuc", m.getRocAuc());
                    add(e, "selected", m.isSelected());
                });
                list(pe, "drivers", "driver", v.drivers(), (e, d) -> {
                    add(e, "name", d.name());
                    add(e, "importance", d.importance());
                });
                add(pe, "disclaimer", DISCLAIMER);
            }
            case "getPredictionHistory" -> {
                String symbol = text(request, "symbol");
                Integer limit = intValue(request, "limit");
                List<Prediction> rows = predictions.history(symbol, limit == null ? 25 : limit);
                PredictionService.Tally t = predictions.tally(symbol);
                add(response, "count", rows.size());
                Element s = group(response, "summary");
                add(s, "total", t.total());
                add(s, "evaluated", t.evaluated());
                add(s, "correct", t.correct());
                add(s, "hitRatePercent", t.hitRatePercent());
                list(response, "predictions", "prediction", rows, this::fill);
            }
            case "getModelPerformance" -> {
                PredictionService.Performance p = predictions.performance(text(request, "symbol"), intValue(request, "horizonDays"));
                list(response, "metrics", "metric", p.metrics(), (e, m) -> {
                    add(e, "symbol", m.getSymbol());
                    add(e, "horizonDays", m.getHorizonDays());
                    add(e, "model", m.getModelName());
                    add(e, "version", m.getModelVersion());
                    add(e, "accuracy", m.getAccuracy());
                    add(e, "precision", m.getPrecisionScore());
                    add(e, "recall", m.getRecallScore());
                    add(e, "f1", m.getF1Score());
                    add(e, "rocAuc", m.getRocAuc());
                    add(e, "trainSamples", m.getTrainSamples());
                    add(e, "testSamples", m.getTestSamples());
                    add(e, "baselineAccuracy", m.getBaselineAccuracy());
                    add(e, "selected", m.isSelected());
                    add(e, "trainedAt", m.getTrainedAt());
                });
                Element live = group(response, "livePerformance");
                add(live, "total", p.live().total());
                add(live, "evaluated", p.live().evaluated());
                add(live, "correct", p.live().correct());
                add(live, "hitRatePercent", p.live().hitRatePercent());
            }
            default -> throw new IllegalStateException("Unhandled operation " + operation);
        }
        return response;
    }

    private void fill(Element e, Prediction p) {
        add(e, "id", p.getId());
        add(e, "symbol", p.getSymbol());
        add(e, "horizonDays", p.getHorizonDays());
        add(e, "direction", p.getDirection());
        add(e, "probabilityUp", p.getProbabilityUp());
        add(e, "probabilityDown", 1.0 - p.getProbabilityUp());
        add(e, "confidence", p.getConfidence());
        add(e, "model", p.getModelName());
        add(e, "modelVersion", p.getModelVersion());
        add(e, "baseDate", p.getBaseDate());
        add(e, "basePrice", p.getBasePrice());
        add(e, "targetDate", p.getTargetDate());
        add(e, "createdAt", p.getCreatedAt());
        add(e, "status", p.getStatus().name());
        add(e, "reliability", p.getReliability());
        add(e, "trainSamples", p.getTrainSamples());
        add(e, "testSamples", p.getTestSamples());
        add(e, "baselineAccuracy", p.getBaselineAccuracy());
        add(e, "actualClose", p.getActualClose());
        add(e, "actualDirection", p.getActualDirection());
        add(e, "correct", p.getCorrect());
        add(e, "evaluatedAt", p.getEvaluatedAt());
    }
}
