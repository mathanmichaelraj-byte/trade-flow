package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Company profile + fundamentals cache, one row per symbol. */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(length = 20)
    private String symbol;
    private String name;
    private String sector;
    private String industry;
    private String exchange;
    @Column(length = 10)
    private String currency;
    private String country;
    private String website;
    @Column(length = 4000)
    private String description;
    private Double marketCap;
    private Double peRatio;
    private Double eps;
    /** Percent, e.g. 0.55 means 0.55%. */
    private Double dividendYield;
    private Double beta;
    private Double week52High;
    private Double week52Low;
    /** Return on equity, percent. */
    private Double roe;
    /** Return on capital employed, percent. */
    private Double roce;
    @Column(length = 20)
    private String dataSource;
    private Instant profileUpdatedAt;
    private Instant historyUpdatedAt;

    protected Stock() {
    }

    public Stock(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description != null && description.length() > 4000 ? description.substring(0, 4000) : description;
    }
    public Double getMarketCap() { return marketCap; }
    public void setMarketCap(Double marketCap) { this.marketCap = marketCap; }
    public Double getPeRatio() { return peRatio; }
    public void setPeRatio(Double peRatio) { this.peRatio = peRatio; }
    public Double getEps() { return eps; }
    public void setEps(Double eps) { this.eps = eps; }
    public Double getDividendYield() { return dividendYield; }
    public void setDividendYield(Double dividendYield) { this.dividendYield = dividendYield; }
    public Double getBeta() { return beta; }
    public void setBeta(Double beta) { this.beta = beta; }
    public Double getWeek52High() { return week52High; }
    public void setWeek52High(Double week52High) { this.week52High = week52High; }
    public Double getWeek52Low() { return week52Low; }
    public void setWeek52Low(Double week52Low) { this.week52Low = week52Low; }
    public Double getRoe() { return roe; }
    public void setRoe(Double roe) { this.roe = roe; }
    public Double getRoce() { return roce; }
    public void setRoce(Double roce) { this.roce = roce; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Instant getProfileUpdatedAt() { return profileUpdatedAt; }
    public void setProfileUpdatedAt(Instant profileUpdatedAt) { this.profileUpdatedAt = profileUpdatedAt; }
    public Instant getHistoryUpdatedAt() { return historyUpdatedAt; }
    public void setHistoryUpdatedAt(Instant historyUpdatedAt) { this.historyUpdatedAt = historyUpdatedAt; }
}
