package com.stocksense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "stocksense")
public record AppProperties(@DefaultValue Market market, @DefaultValue Ml ml) {

    public record Market(
            @DefaultValue({"yahoo", "twelvedata", "simulated"}) List<String> providers,
            @DefaultValue("") String twelveDataKey,
            @DefaultValue("https://query1.finance.yahoo.com") String yahooBaseUrl,
            @DefaultValue("https://api.twelvedata.com") String twelveDataBaseUrl,
            @DefaultValue("10") int timeoutSeconds) {
    }

    public record Ml(
            @DefaultValue("http://localhost:8000") String baseUrl,
            @DefaultValue("180") int timeoutSeconds) {
    }
}
