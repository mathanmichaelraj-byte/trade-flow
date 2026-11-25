package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Hold-out evaluation of one candidate model from one training run. */
@Entity
@Table(name = "model_metrics", indexes = @Index(name = "ix_metric_symbol", columnList = "symbol,horizon_days,trained_at"))
public class ModelMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 20, nullable = false)
    private String symbol;
    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;
    @Column(name = "model_name", length = 40, nullable = false)
    private String modelName;
    @Column(name = "model_version", length = 60, nullable = false)
    private String modelVersion;
    private double accuracy;
    @Column(name = "precision_score")
    private double precisionScore;
    @Column(name = "recall_score")
    private double recallScore;
    @Column(name = "f1_score")
    private double f1Score;
    private Double rocAuc;
    private Integer trainSamples;
    private Integer testSamples;
    private Double baselineAccuracy;
    @Column(name = "selected_model")
    private boolean selected;
    @Column(name = "trained_at", nullable = false)
    private Instant trainedAt;

    protected ModelMetric() {
    }

    public ModelMetric(String symbol, int horizonDays, String modelName, String modelVersion, double accuracy,
                       double precisionScore, double recallScore, double f1Score, Double rocAuc,
                       Integer trainSamples, Integer testSamples, Double baselineAccuracy, boolean selected,
                       Instant trainedAt) {
        this.symbol = symbol;
        this.horizonDays = horizonDays;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.accuracy = accuracy;
        this.precisionScore = precisionScore;
        this.recallScore = recallScore;
        this.f1Score = f1Score;
        this.rocAuc = rocAuc;
        this.trainSamples = trainSamples;
        this.testSamples = testSamples;
        this.baselineAccuracy = baselineAccuracy;
        this.selected = selected;
        this.trainedAt = trainedAt;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public int getHorizonDays() { return horizonDays; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public double getAccuracy() { return accuracy; }
    public double getPrecisionScore() { return precisionScore; }
    public double getRecallScore() { return recallScore; }
    public double getF1Score() { return f1Score; }
    public Double getRocAuc() { return rocAuc; }
    public Integer getTrainSamples() { return trainSamples; }
    public Integer getTestSamples() { return testSamples; }
    public Double getBaselineAccuracy() { return baselineAccuracy; }
    public boolean isSelected() { return selected; }
    public Instant getTrainedAt() { return trainedAt; }
}
