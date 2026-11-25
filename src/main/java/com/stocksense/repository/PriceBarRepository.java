package com.stocksense.repository;

import com.stocksense.domain.PriceBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    List<PriceBar> findBySymbolOrderByTradeDateAsc(String symbol);

    List<PriceBar> findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(String symbol, LocalDate from);

    long countBySymbol(String symbol);

    @Modifying
    @Transactional
    @Query("delete from PriceBar b where b.symbol = :symbol")
    int deleteAllBySymbol(@Param("symbol") String symbol);
}
