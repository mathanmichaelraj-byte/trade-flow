package com.stocksense.repository;

import com.stocksense.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByOwnerOrderByAddedAtAsc(String owner);

    Optional<WatchlistItem> findByOwnerAndSymbol(String owner, String symbol);

    long countByOwner(String owner);
}
