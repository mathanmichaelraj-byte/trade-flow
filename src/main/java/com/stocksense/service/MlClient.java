package com.stocksense.service;

import com.stocksense.config.AppProperties;
import com.stocksense.config.HttpSupport;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.util.Json;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** REST client for the Python FastAPI machine-learning service. */
@Component
public class MlClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    public record ModelScore(String name, double accuracy, double precision, double recall, double f1,
                             Double rocAuc, boolean selected) {
    }

    public record Driver(String name, double importance) {
    }

    public record Result(String direction, double probabilityUp, double confidence, String modelName,
                         String modelVersion, Instant trainedAt, int trainSamples, int testSamples,
                         double baselineAccuracy, String reliability, List<ModelScore> models, List<Driver> drivers) {
    }

    private final RestClient client;
    private final RestClient healthClient;
    private final String baseUrl;

    public MlClient(RestClient.Builder builder, AppProperties props) {
        this.baseUrl = props.ml().baseUrl();
        this.client = HttpSupport.configure(builder.clone(), baseUrl, props.ml().timeoutSeconds()).build();
        this.healthClient = HttpSupport.configure(builder.clone(), baseUrl, 3).build();
    }

    public Result predict(String symbol, int horizonDays, List<Bar> bars, boolean forceRetrain) {
        List<Map<String, Object>> rows = new ArrayList<>(bars.size());
        for (Bar b : bars) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", b.date().toString());
            row.put("open", b.open());
            row.put("high", b.high());
            row.put("low", b.low());
            row.put("close", b.close());
            row.put("volume", b.volume());
            rows.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("symbol", symbol);
        request.put("horizon_days", horizonDays);
        request.put("force_retrain", forceRetrain);
        request.put("bars", rows);

        Map<String, Object> body;
        try {
            body = client.post().uri("/predict").contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve().body(MAP);
        } catch (HttpStatusCodeException e) {
            String detail = detail(e);
            if (e.getStatusCode().value() == 400) {
                throw new StockSenseException.InsufficientData(detail);
            }
            throw new StockSenseException.MlService("ML service error (" + e.getStatusCode().value() + "): " + detail);
        } catch (ResourceAccessException e) {
            throw new StockSenseException.MlService("ML service is not reachable at " + baseUrl
                    + " (or timed out). Start it with: cd ml-service && uvicorn app.main:app --port 8000");
        } catch (RestClientException e) {
            throw new StockSenseException.MlService("ML service call failed: " + e.getMessage());
        }
        if (body == null || body.get("direction") == null) {
            throw new StockSenseException.MlService("ML service returned an invalid response");
        }
        return parse(body);
    }

    /** Returns a short status string, or throws when the service cannot be reached. */
    public String health() {
        try {
            Map<String, Object> body = healthClient.get().uri("/health").retrieve().body(MAP);
            String status = Json.str(body == null ? null : body.get("status"));
            return status == null ? "unknown" : status;
        } catch (RestClientException e) {
            throw new StockSenseException.MlService("unreachable");
        }
    }

    private static Result parse(Map<String, Object> b) {
        List<ModelScore> models = new ArrayList<>();
        for (Object o : Json.list(b.get("models"))) {
            Map<String, Object> m = Json.obj(o);
            models.add(new ModelScore(String.valueOf(m.get("name")), d(m.get("accuracy")), d(m.get("precision")),
                    d(m.get("recall")), d(m.get("f1")), Json.dbl(m.get("roc_auc")), Boolean.TRUE.equals(m.get("selected"))));
        }
        List<Driver> drivers = new ArrayList<>();
        for (Object o : Json.list(b.get("top_features"))) {
            Map<String, Object> f = Json.obj(o);
            drivers.add(new Driver(String.valueOf(f.get("name")), d(f.get("importance"))));
        }
        Instant trainedAt;
        try {
            trainedAt = Instant.parse(String.valueOf(b.get("trained_at")));
        } catch (RuntimeException e) {
            trainedAt = Instant.now();
        }
        Long train = Json.lng(b.get("train_samples"));
        Long test = Json.lng(b.get("test_samples"));
        return new Result(String.valueOf(b.get("direction")), d(b.get("probability_up")), d(b.get("confidence")),
                String.valueOf(b.get("model")), String.valueOf(b.get("model_version")), trainedAt,
                train == null ? 0 : train.intValue(), test == null ? 0 : test.intValue(),
                d(b.get("baseline_accuracy")), String.valueOf(b.get("reliability")), models, drivers);
    }

    private static double d(Object o) {
        Double v = Json.dbl(o);
        return v == null ? 0.0 : v;
    }

    private static String detail(HttpStatusCodeException e) {
        try {
            Object d = Json.obj(e.getResponseBodyAs(Map.class)).get("detail");
            if (d != null) {
                return String.valueOf(d);
            }
        } catch (RuntimeException ignored) {
            // fall through to raw body
        }
        String raw = e.getResponseBodyAsString();
        return raw.length() > 300 ? raw.substring(0, 300) : raw;
    }
}
