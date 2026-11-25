package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** One daily OHLCV bar. */
@Entity
@Table(name = "price_bars",
        uniqueConstraints = @UniqueConstraint(name = "uk_bar_symbol_date", columnNames = {"symbol", "trade_date"}),
        indexes = @Index(name = "ix_bar_symbol_date", columnList = "symbol,trade_date"))
public class PriceBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
    @Column(name = "open_price", nullable = false)
    private double openPrice;
    @Column(name = "high_price", nullable = false)
    private double highPrice;
    @Column(name = "low_price", nullable = false)
    private double lowPrice;
    @Column(name = "close_price", nullable = false)
    private double closePrice;
    @Column(name = "volume", nullable = false)
    private long volume;

    protected PriceBar() {
    }

    public PriceBar(String symbol, LocalDate tradeDate, double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
        this.openPrice = open;
        this.highPrice = high;
        this.lowPrice = low;
        this.closePrice = close;
        this.volume = volume;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getTradeDate() { return tradeDate; }
    public double getOpenPrice() { return openPrice; }
    public double getHighPrice() { return highPrice; }
    public double getLowPrice() { return lowPrice; }
    public double getClosePrice() { return closePrice; }
    public long getVolume() { return volume; }

    public void update(double open, double high, double low, double close, long volume) {
        this.openPrice = open;
        this.highPrice = high;
        this.lowPrice = low;
        this.closePrice = close;
        this.volume = volume;
    }
}
