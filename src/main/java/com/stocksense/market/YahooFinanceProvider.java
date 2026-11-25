package com.stocksense.market;

import com.stocksense.config.AppProperties;
import com.stocksense.config.HttpSupport;
import com.stocksense.exception.StockSenseException;
import com.stocksense.market.MarketModels.Bar;
import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;
import com.stocksense.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yahoo Finance via its public chart/search endpoints (no API key).
 * Fundamentals use quoteSummary, which needs a cookie + crumb; if that handshake fails the profile
 * degrades gracefully to the data available from the chart endpoint.
 */
@Component
public class YahooFinanceProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceProvider.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final long CRUMB_TTL_MS = 6 * 3600_000L;

    private final RestClient client;
    private final String baseUrl;
    private volatile String crumb;
    private volatile String cookie;
    private volatile long crumbAt;

    public YahooFinanceProvider(RestClient.Builder builder, AppProperties props) {
        AppProperties.Market m = props.market();
        this.baseUrl = m.yahooBaseUrl();
        this.client = HttpSupport.configure(builder, baseUrl, m.timeoutSeconds())
                .defaultHeader("User-Agent", UA)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "yahoo";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        Map<String, Object> body = get("search", query, "/v1/finance/search?q={q}&quotesCount={n}&newsCount=0", query, limit);
        List<SearchHit> hits = new ArrayList<>();
        for (Object o : Json.list(body.get("quotes"))) {
            Map<String, Object> q = Json.obj(o);
            String type = Json.str(q.get("quoteType"));
            String symbol = Json.str(q.get("symbol"));
            if (symbol == null || type == null || !(type.equals("EQUITY") || type.equals("ETF") || type.equals("INDEX"))) {
                continue;
            }
            String name = Json.str(q.get("longname"));
            if (name == null) {
                name = Json.str(q.get("shortname"));
            }
            String exch = Json.str(q.get("exchDisp"));
            hits.add(new SearchHit(symbol, name == null ? symbol : name, exch == null ? Json.str(q.get("exchange")) : exch, type));
        }
        return hits;
    }

    @Override
    public QuoteData quote(String symbol) {
        Map<String, Object> result = chart(symbol, "range=5d&interval=1d");
        List<Bar> bars = parseBars(result);
        Map<String, Object> meta = Json.obj(result.get("meta"));
        Double price = Json.dbl(meta.get("regularMarketPrice"));
        if (price == null && !bars.isEmpty()) {
            price = bars.get(bars.size() - 1).close();
        }
        if (price == null) {
            throw new StockSenseException.Provider("Yahoo returned no price for " + symbol);
        }
        Bar last = bars.isEmpty() ? null : bars.get(bars.size() - 1);
        double prev;
        if (bars.size() >= 2) {
            prev = bars.get(bars.size() - 2).close();
        } else {
            Double p = Json.dbl(meta.get("chartPreviousClose"));
            prev = p != null ? p : price;
        }
        double open = last != null ? last.open() : price;
        double high = nz(Json.dbl(meta.get("regularMarketDayHigh")), last != null ? last.high() : price);
        double low = nz(Json.dbl(meta.get("regularMarketDayLow")), last != null ? last.low() : price);
        long volume = last != null ? last.volume() : 0L;
        Long metaVol = Json.lng(meta.get("regularMarketVolume"));
        if (metaVol != null && metaVol > 0) {
            volume = metaVol;
        }
        return new QuoteData(symbol, displayName(meta, symbol), price, prev, open, high, low, volume,
                Json.str(meta.get("currency")), Json.str(meta.get("fullExchangeName") != null
                ? meta.get("fullExchangeName") : meta.get("exchangeName")), name(), Instant.now());
    }

    @Override
    public List<Bar> history(String symbol, int days) {
        long to = Instant.now().getEpochSecond();
        long from = LocalDate.now(ZoneOffset.UTC).minusDays(days).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        List<Bar> bars = parseBars(chart(symbol, "period1=" + from + "&period2=" + to + "&interval=1d"));
        if (bars.isEmpty()) {
            throw new StockSenseException.Provider("Yahoo returned no bars for " + symbol);
        }
        return bars;
    }

    @Override
    public CompanyProfile profile(String symbol) {
        Map<String, Object> meta = Json.obj(chart(symbol, "range=5d&interval=1d").get("meta"));
        String name = displayName(meta, symbol);
        String exchange = Json.str(meta.get("fullExchangeName") != null ? meta.get("fullExchangeName") : meta.get("exchangeName"));
        String currency = Json.str(meta.get("currency"));
        Double high52 = Json.dbl(meta.get("fiftyTwoWeekHigh"));
        Double low52 = Json.dbl(meta.get("fiftyTwoWeekLow"));
        String sector = null, industry = null, country = null, website = null, description = null;
        Double marketCap = null, pe = null, eps = null, divYield = null, beta = null, roe = null, roce = null;
        try {
            Map<String, Object> s = summary(symbol);
            Map<String, Object> price = Json.obj(s.get("price"));
            Map<String, Object> detail = Json.obj(s.get("summaryDetail"));
            Map<String, Object> stats = Json.obj(s.get("defaultKeyStatistics"));
            Map<String, Object> asset = Json.obj(s.get("assetProfile"));
            String longName = Json.str(price.get("longName"));
            if (longName != null) {
                name = longName;
            }
            marketCap = firstNonNull(Json.dbl(price.get("marketCap")), Json.dbl(detail.get("marketCap")));
            pe = Json.dbl(detail.get("trailingPE"));
            eps = Json.dbl(stats.get("trailingEps"));
            Double dy = Json.dbl(detail.get("dividendYield"));
            divYield = dy == null ? null : dy * 100.0;
            beta = firstNonNull(Json.dbl(detail.get("beta")), Json.dbl(stats.get("beta")));
            Double r = Json.dbl(Json.obj(s.get("financialData")).get("returnOnEquity"));
            roe = r == null ? null : r * 100.0; // Yahoo returns a fraction
            high52 = firstNonNull(Json.dbl(detail.get("fiftyTwoWeekHigh")), high52);
            low52 = firstNonNull(Json.dbl(detail.get("fiftyTwoWeekLow")), low52);
            sector = Json.str(asset.get("sector"));
            industry = Json.str(asset.get("industry"));
            country = Json.str(asset.get("country"));
            website = Json.str(asset.get("website"));
            description = Json.str(asset.get("longBusinessSummary"));
        } catch (RuntimeException e) {
            log.debug("Yahoo fundamentals unavailable for {}: {}", symbol, e.getMessage());
        }
        try {
            roce = roce(symbol);
        } catch (RuntimeException e) {
            log.debug("ROCE unavailable for {}: {}", symbol, e.getMessage());
        }
        return new CompanyProfile(symbol, name, sector, industry, exchange, currency, country, website, description,
                marketCap, pe, eps, divYield, beta, high52, low52, roe, roce, name());
    }

    /** ROCE % = EBIT / (total assets - current liabilities) from the latest annual statements; null if incomplete. */
    private Double roce(String symbol) {
        long to = Instant.now().getEpochSecond();
        long from = LocalDate.now(ZoneOffset.UTC).minusYears(5).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        Map<String, Object> body = get("fundamentals", symbol,
                "/ws/fundamentals-timeseries/v1/finance/timeseries/{symbol}?type=annualEBIT,annualTotalAssets,annualCurrentLiabilities&period1={p1}&period2={p2}",
                symbol, from, to);
        Double ebit = null, assets = null, liabilities = null;
        for (Object o : Json.list(Json.at(body, "timeseries", "result"))) {
            Map<String, Object> r = Json.obj(o);
            String type = Json.str(Json.at(r, "meta", "type", 0));
            if (type == null) {
                continue;
            }
            List<Object> points = Json.list(r.get(type));
            Double latest = null;
            for (int i = points.size() - 1; i >= 0 && latest == null; i--) {
                latest = Json.dbl(Json.at(points.get(i), "reportedValue", "raw"));
            }
            switch (type) {
                case "annualEBIT" -> ebit = latest;
                case "annualTotalAssets" -> assets = latest;
                case "annualCurrentLiabilities" -> liabilities = latest;
                default -> { }
            }
        }
        if (ebit == null || assets == null || liabilities == null || assets - liabilities <= 0) {
            return null;
        }
        return ebit / (assets - liabilities) * 100.0;
    }

    // ------------------------------------------------------------------

    private Map<String, Object> chart(String symbol, String query) {
        Map<String, Object> body = get("chart", symbol, "/v8/finance/chart/{symbol}?" + query, symbol);
        Map<String, Object> error = Json.obj(Json.at(body, "chart", "error"));
        if (!error.isEmpty()) {
            String code = String.valueOf(error.get("code"));
            if (code.equalsIgnoreCase("Not Found")) {
                throw new StockSenseException.SymbolNotFound(symbol);
            }
            throw new StockSenseException.Provider("Yahoo error: " + code);
        }
        Map<String, Object> result = Json.obj(Json.at(body, "chart", "result", 0));
        if (result.isEmpty()) {
            throw new StockSenseException.SymbolNotFound(symbol);
        }
        return result;
    }

    private List<Bar> parseBars(Map<String, Object> result) {
        List<Object> ts = Json.list(result.get("timestamp"));
        Map<String, Object> q = Json.obj(Json.at(result, "indicators", "quote", 0));
        List<Object> o = Json.list(q.get("open"));
        List<Object> h = Json.list(q.get("high"));
        List<Object> l = Json.list(q.get("low"));
        List<Object> c = Json.list(q.get("close"));
        List<Object> v = Json.list(q.get("volume"));
        Long off = Json.lng(Json.at(result, "meta", "gmtoffset"));
        long offset = off == null ? 0 : off;
        Map<LocalDate, Bar> byDate = new LinkedHashMap<>();
        for (int i = 0; i < ts.size(); i++) {
            Long t = Json.lng(ts.get(i));
            Double close = at(c, i);
            if (t == null || close == null || close <= 0) {
                continue;
            }
            double open = nz(at(o, i), close);
            double high = nz(at(h, i), Math.max(open, close));
            double low = nz(at(l, i), Math.min(open, close));
            Double vol = at(v, i);
            LocalDate date = Instant.ofEpochSecond(t + offset).atZone(ZoneOffset.UTC).toLocalDate();
            byDate.put(date, new Bar(date, open, Math.max(high, Math.max(open, close)),
                    Math.min(low, Math.min(open, close)), close, vol == null ? 0L : vol.longValue()));
        }
        return new ArrayList<>(byDate.values());
    }

    private Map<String, Object> summary(String symbol) {
        ensureCrumb();
        Map<String, Object> body = client.get()
                .uri("/v10/finance/quoteSummary/{symbol}?modules=price,summaryDetail,defaultKeyStatistics,assetProfile,financialData&crumb={crumb}",
                        symbol, crumb)
                .header("Cookie", cookie)
                .retrieve()
                .body(MAP);
        return Json.obj(Json.at(body, "quoteSummary", "result", 0));
    }

    private synchronized void ensureCrumb() {
        if (crumb != null && System.currentTimeMillis() - crumbAt < CRUMB_TTL_MS) {
            return;
        }
        String c = client.get().uri(URI.create("https://fc.yahoo.com")).exchange((req, res) -> {
            List<String> cookies = res.getHeaders().get("Set-Cookie");
            return cookies == null || cookies.isEmpty() ? null : cookies.get(0).split(";")[0];
        });
        if (c == null) {
            throw new StockSenseException.Provider("Yahoo cookie handshake failed");
        }
        String cr = client.get().uri(URI.create(baseUrl + "/v1/test/getcrumb")).header("Cookie", c)
                .retrieve().body(String.class);
        if (cr == null || cr.isBlank() || cr.contains(" ") || cr.contains("<") || cr.length() > 40) {
            throw new StockSenseException.Provider("Yahoo crumb unavailable");
        }
        cookie = c;
        crumb = cr.trim();
        crumbAt = System.currentTimeMillis();
    }

    private Map<String, Object> get(String op, String subject, String template, Object... vars) {
        try {
            Map<String, Object> body = client.get().uri(template, vars).retrieve().body(MAP);
            return body == null ? Map.of() : body;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new StockSenseException.SymbolNotFound(subject);
            }
            throw new StockSenseException.Provider("Yahoo " + op + " HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new StockSenseException.Provider("Yahoo " + op + " failed: " + e.getMessage());
        }
    }

    private static String displayName(Map<String, Object> meta, String symbol) {
        String n = Json.str(meta.get("longName"));
        if (n == null) {
            n = Json.str(meta.get("shortName"));
        }
        return n == null ? symbol : n;
    }

    private static Double at(List<Object> list, int i) {
        return i < list.size() ? Json.dbl(list.get(i)) : null;
    }

    private static double nz(Double v, double fallback) {
        return v == null ? fallback : v;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
