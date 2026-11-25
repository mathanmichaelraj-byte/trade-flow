package com.stocksense.repository;

import com.stocksense.domain.ModelMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelMetricRepository extends JpaRepository<ModelMetric, Long> {

    boolean existsBySymbolAndHorizonDaysAndModelVersion(String symbol, int horizonDays, String modelVersion);

    List<ModelMetric> findBySymbolAndHorizonDaysAndModelVersionOrderByAccuracyDesc(String symbol, int horizonDays, String modelVersion);

    List<ModelMetric> findAllByOrderByTrainedAtDesc(Pageable pageable);

    List<ModelMetric> findBySymbolOrderByTrainedAtDesc(String symbol, Pageable pageable);
}
