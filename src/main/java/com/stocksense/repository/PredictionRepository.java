package com.stocksense.repository;

import com.stocksense.domain.Prediction;
import com.stocksense.domain.PredictionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findFirstBySymbolAndHorizonDaysAndBaseDate(String symbol, int horizonDays, LocalDate baseDate);

    List<Prediction> findBySymbolOrderByCreatedAtDesc(String symbol, Pageable pageable);

    List<Prediction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Prediction> findBySymbolAndStatus(String symbol, PredictionStatus status);

    @Query("select distinct p.symbol from Prediction p where p.status = com.stocksense.domain.PredictionStatus.PENDING")
    List<String> findPendingSymbols();

    long count();

    long countBySymbol(String symbol);

    long countByStatus(PredictionStatus status);

    long countBySymbolAndStatus(String symbol, PredictionStatus status);

    long countByStatusAndCorrectTrue(PredictionStatus status);

    long countBySymbolAndStatusAndCorrectTrue(String symbol, PredictionStatus status);
}
