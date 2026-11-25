package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A stored model prediction and, once the horizon has passed, what actually happened. */
@Entity
@Table(name = "predictions",
        indexes = {@Index(name = "ix_pred_symbol", columnList = "symbol,created_at"),
                @Index(name = "ix_pred_status", columnList = "status")})
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 20, nullable = false)
    private String symbol;
    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;
    @Column(length = 8, nullable = false)
    private String direction;
    @Column(name = "probability_up", nullable = false)
    private double probabilityUp;
    @Column(nullable = false)
    private double confidence;
    @Column(name = "model_name", length = 40, nullable = false)
    private String modelName;
    @Column(name = "model_version", length = 60, nullable = false)
    private String modelVersion;
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;
    @Column(name = "base_price", nullable = false)
    private double basePrice;
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private PredictionStatus status = PredictionStatus.PENDING;
    private Integer trainSamples;
    private Integer testSamples;
    private Double baselineAccuracy;
    @Column(length = 12)
    private String reliability;
    /** "name=importance;name=importance" */
    @Column(length = 600)
    private String drivers;
    private Double actualClose;
    @Column(length = 8)
    private String actualDirection;
    private Boolean correct;
    private Instant evaluatedAt;

    protected Prediction() {
    }

    public Prediction(String symbol, int horizonDays, String direction, double probabilityUp, double confidence,
                      String modelName, String modelVersion, LocalDate baseDate, double basePrice, LocalDate targetDate) {
        this.symbol = symbol;
        this.horizonDays = horizonDays;
        this.direction = direction;
        this.probabilityUp = probabilityUp;
        this.confidence = confidence;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.baseDate = baseDate;
        this.basePrice = basePrice;
        this.targetDate = targetDate;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public int getHorizonDays() { return horizonDays; }
    public String getDirection() { return direction; }
    public double getProbabilityUp() { return probabilityUp; }
    public double getConfidence() { return confidence; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public LocalDate getBaseDate() { return baseDate; }
    public double getBasePrice() { return basePrice; }
    public LocalDate getTargetDate() { return targetDate; }
    public Instant getCreatedAt() { return createdAt; }
    public PredictionStatus getStatus() { return status; }
    public Integer getTrainSamples() { return trainSamples; }
    public void setTrainSamples(Integer trainSamples) { this.trainSamples = trainSamples; }
    public Integer getTestSamples() { return testSamples; }
    public void setTestSamples(Integer testSamples) { this.testSamples = testSamples; }
    public Double getBaselineAccuracy() { return baselineAccuracy; }
    public void setBaselineAccuracy(Double baselineAccuracy) { this.baselineAccuracy = baselineAccuracy; }
    public String getReliability() { return reliability; }
    public void setReliability(String reliability) { this.reliability = reliability; }
    public String getDrivers() { return drivers; }
    public void setDrivers(String drivers) { this.drivers = drivers; }
    public Double getActualClose() { return actualClose; }
    public String getActualDirection() { return actualDirection; }
    public Boolean getCorrect() { return correct; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public void markEvaluated(double actualClose) {
        this.actualClose = actualClose;
        this.actualDirection = actualClose > basePrice ? "UP" : "DOWN";
        this.correct = actualDirection.equals(direction);
        this.status = PredictionStatus.EVALUATED;
        this.evaluatedAt = Instant.now();
    }
}
