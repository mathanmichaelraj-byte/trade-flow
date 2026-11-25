package com.stocksense.market;

import com.stocksense.config.AppProperties;
import com.stocksense.config.HttpSupport;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;
import com.stocksense.util.Json;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Twelve Data REST API. Enabled only when TWELVE_DATA_API_KEY is set. */
@Component
public class TwelveDataProvider implements MarketDataProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;
    private final String apiKey;

    public TwelveDataProvider(RestClient.Builder builder, AppProperties props) {
        AppProperties.Market m = props.market();
        this.apiKey = m.twelveDataKey();
        this.client = HttpSupport.configure(builder, m.twelveDataBaseUrl(), m.timeoutSeconds()).build();
    }

    @Override
    public String name() {
        return "twelvedata";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        Map<String, Object> body = call(query, "/symbol_search?symbol={q}&outputsize={n}&apikey={k}", query, limit, apiKey);
        List<SearchHit> hits = new ArrayList<>();
        for (Object o : Json.list(body.get("data"))) {
            Map<String, Object> d = Json.obj(o);
            String sym = Json.str(d.get("symbol"));
            if (sym == null) {
                continue;
            }
            String name = Json.str(d.get("instrument_name"));
            hits.add(new SearchHit(sym, name == null ? sym : name, Json.str(d.get("exchange")), Json.str(d.get("instrument_type"))));
        }
        return hits;
    }

    @Override
    public QuoteData quote(String symbol) {
        Map<String, Object> q = call(symbol, "/quote?symbol={s}&apikey={k}", symbol, apiKey);
        Double price = Json.dbl(q.get("close"));
        if (price == null) {
            throw new StockSenseException.Provider("Twelve Data returned no price for " + symbol);
        }
        Double prev = Json.dbl(q.get("previous_close"));
        double open = nz(Json.dbl(q.get("open")), price);
        Long volume = Json.lng(q.get("volume"));
        String name = Json.str(q.get("name"));
        return new QuoteData(symbol, name == null ? symbol : name, price, prev == null ? price : prev, open,
                nz(Json.dbl(q.get("high")), price), nz(Json.dbl(q.get("low")), price), volume == null ? 0L : volume,
                Json.str(q.get("currency")), Json.str(q.get("exchange")), name(), Instant.now());
    }

    @Override
    public List<Bar> history(String symbol, int days) {
        int size = (int) Math.min(5000, Math.max(30, Math.round(days * 0.72) + 10));
        Map<String, Object> body = call(symbol, "/time_series?symbol={s}&interval=1day&outputsize={n}&apikey={k}",
                symbol, size, apiKey);
        List<Bar> bars = new ArrayList<>();
        for (Object o : Json.list(body.get("values"))) {
            Map<String, Object> v = Json.obj(o);
            String dt = Json.str(v.get("datetime"));
            Double close = Json.dbl(v.get("close"));
            if (dt == null || dt.length() < 10 || close == null || close <= 0) {
                continue;
            }
            double open = nz(Json.dbl(v.get("open")), close);
            double high = nz(Json.dbl(v.get("high")), Math.max(open, close));
            double low = nz(Json.dbl(v.get("low")), Math.min(open, close));
            Long vol = Json.lng(v.get("volume"));
            bars.add(new Bar(LocalDate.parse(dt.substring(0, 10)), open, Math.max(high, Math.max(open, close)),
                    Math.min(low, Math.min(open, close)), close, vol == null ? 0L : vol));
        }
        if (bars.isEmpty()) {
            throw new StockSenseException.Provider("Twelve Data returned no bars for " + symbol);
        }
        Collections.reverse(bars); // API returns newest first
        return bars;
    }

    @Override
    public CompanyProfile profile(String symbol) {
        Map<String, Object> raw = call(symbol, "/quote?symbol={s}&apikey={k}", symbol, apiKey);
        String qName = Json.str(raw.get("name"));
        Double high52 = Json.dbl(Json.at(raw, "fifty_two_week", "high"));
        Double low52 = Json.dbl(Json.at(raw, "fifty_two_week", "low"));
        String sector = null, industry = null, country = null, website = null, description = null;
        Double marketCap = null, pe = null, eps = null, divYield = null, beta = null, roe = null;
        try { // profile + statistics are plan-limited; ignore failures
            Map<String, Object> p = call(symbol, "/profile?symbol={s}&apikey={k}", symbol, apiKey);
            sector = Json.str(p.get("sector"));
            industry = Json.str(p.get("industry"));
            country = Json.str(p.get("country"));
            website = Json.str(p.get("website"));
            description = Json.str(p.get("description"));
        } catch (RuntimeException ignored) {
            // not available on this plan
        }
        try {
            Map<String, Object> s = Json.obj(call(symbol, "/statistics?symbol={s}&apikey={k}", symbol, apiKey).get("statistics"));
            marketCap = Json.dbl(Json.at(s, "valuations_metrics", "market_capitalization"));
            pe = Json.dbl(Json.at(s, "valuations_metrics", "trailing_pe"));
            eps = Json.dbl(Json.at(s, "financials", "income_statement", "diluted_eps_ttm"));
            divYield = Json.dbl(Json.at(s, "dividends_and_splits", "trailing_annual_dividend_yield"));
            if (divYield != null) {
                divYield = divYield * 100.0;
            }
            beta = Json.dbl(Json.at(s, "stock_price_summary", "beta"));
            Double r = Json.dbl(Json.at(s, "financials", "return_on_equity_ttm"));
            roe = r == null ? null : r * 100.0; // API returns a fraction
        } catch (RuntimeException ignored) {
            // not available on this plan
        }
        return new CompanyProfile(symbol, qName == null ? symbol : qName, sector, industry, Json.str(raw.get("exchange")), Json.str(raw.get("currency")), country, website,
                description, marketCap, pe, eps, divYield, beta, high52, low52, roe, null, name()); // ROCE: not offered by Twelve Data
    }

    private Map<String, Object> call(String subject, String template, Object... vars) {
        Map<String, Object> body;
        try {
            body = client.get().uri(template, vars).retrieve().body(MAP);
        } catch (RestClientException e) {
            throw new StockSenseException.Provider("Twelve Data request failed: " + e.getMessage());
        }
        if (body == null) {
            throw new StockSenseException.Provider("Twelve Data returned an empty response");
        }
        if ("error".equals(body.get("status"))) {
            Long code = Json.lng(body.get("code"));
            String msg = String.valueOf(body.get("message"));
            if (code != null && (code == 404 || code == 400)) {
                throw new StockSenseException.SymbolNotFound(subject);
            }
            throw new StockSenseException.Provider("Twelve Data error " + code + ": " + msg);
        }
        return body;
    }

    private static double nz(Double v, double fallback) {
        return v == null ? fallback : v;
    }
}
