package com.stocksense.domain;

public enum PredictionStatus {
    /** Target date not reached yet. */
    PENDING,
    /** Compared against the actual outcome. */
    EVALUATED
}
