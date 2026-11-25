package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_watch_owner_symbol", columnNames = {"owner_key", "symbol"}))
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_key", length = 64, nullable = false)
    private String owner;
    @Column(length = 20, nullable = false)
    private String symbol;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    protected WatchlistItem() {
    }

    public WatchlistItem(String owner, String symbol) {
        this.owner = owner;
        this.symbol = symbol;
    }

    public Long getId() { return id; }
    public String getOwner() { return owner; }
    public String getSymbol() { return symbol; }
    public Instant getAddedAt() { return addedAt; }
}
