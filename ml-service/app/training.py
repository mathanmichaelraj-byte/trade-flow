"""Model training, hold-out evaluation, selection and inference."""
from __future__ import annotations

import logging
from datetime import datetime, timezone

import numpy as np
import pandas as pd
from sklearn.base import clone
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, roc_auc_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from .config import MIN_LABELED, RANDOM_STATE
from .features import FEATURE_COLUMNS, build_features, make_labels

log = logging.getLogger("stocksense.ml")

try:
    from xgboost import XGBClassifier

    HAS_XGBOOST = True
except Exception:  # pragma: no cover - optional dependency
    HAS_XGBOOST = False


class MlError(Exception):
    """Bad or insufficient input data (mapped to HTTP 400)."""


def candidate_models() -> dict:
    models = {
        "Logistic Regression": Pipeline([
            ("scale", StandardScaler()),
            ("clf", LogisticRegression(C=0.5, max_iter=2000, class_weight="balanced")),
        ]),
        "Random Forest": RandomForestClassifier(
            n_estimators=300, max_depth=6, min_samples_leaf=10, class_weight="balanced_subsample",
            n_jobs=-1, random_state=RANDOM_STATE),
    }
    if HAS_XGBOOST:
        models["XGBoost"] = XGBClassifier(
            n_estimators=250, max_depth=3, learning_rate=0.05, subsample=0.8, colsample_bytree=0.8,
            eval_metric="logloss", random_state=RANDOM_STATE, n_jobs=2)
    return models


def _importances(model, columns: list[str]) -> list[dict]:
    est = model.named_steps["clf"] if isinstance(model, Pipeline) else model
    if hasattr(est, "coef_"):
        raw = np.abs(est.coef_[0])
    elif hasattr(est, "feature_importances_"):
        raw = np.asarray(est.feature_importances_, dtype=float)
    else:
        return []
    total = float(raw.sum()) or 1.0
    order = np.argsort(raw)[::-1][:5]
    return [{"name": columns[i], "importance": float(raw[i] / total)} for i in order]


def train(symbol: str, horizon: int, df: pd.DataFrame) -> dict:
    """Train all candidates on a chronological split (with an h-row embargo), pick the best, refit on all data."""
    feats = build_features(df)
    labels = make_labels(df["close"], horizon)
    data = feats.assign(y=labels).dropna()
    n = len(data)
    if n < MIN_LABELED:
        raise MlError(f"Not enough usable history: {n} labelled samples, need at least {MIN_LABELED}")

    test_n = max(40, int(n * 0.2))
    split = n - test_n - horizon  # embargo: labels of the last `horizon` train rows overlap the test period
    if split < 100:
        raise MlError("Not enough history to hold out a test set for this horizon")

    X, y = data[FEATURE_COLUMNS], data["y"].astype(int)
    X_tr, y_tr = X.iloc[:split], y.iloc[:split]
    X_te, y_te = X.iloc[split + horizon:], y.iloc[split + horizon:]
    if y_tr.nunique() < 2:
        raise MlError("Training data contains only one outcome class")

    majority = int(y_tr.mode().iloc[0])
    baseline = float((y_te == majority).mean())

    scores, fitted = [], {}
    for name, est in candidate_models().items():
        try:
            m = clone(est).fit(X_tr, y_tr)
            pred = m.predict(X_te)
            proba = m.predict_proba(X_te)[:, list(m.classes_).index(1)]
            auc = float(roc_auc_score(y_te, proba)) if y_te.nunique() == 2 else None
            scores.append({
                "name": name,
                "accuracy": float(accuracy_score(y_te, pred)),
                "precision": float(precision_score(y_te, pred, zero_division=0)),
                "recall": float(recall_score(y_te, pred, zero_division=0)),
                "f1": float(f1_score(y_te, pred, zero_division=0)),
                "roc_auc": auc,
                "selected": False,
            })
            fitted[name] = est
        except Exception as exc:  # one failing candidate must not kill the run
            log.warning("model %s failed for %s: %s", name, symbol, exc)
    if not scores:
        raise MlError("All candidate models failed to train")

    best = max(scores, key=lambda s: (s["roc_auc"] if s["roc_auc"] is not None else s["accuracy"], s["accuracy"]))
    best["selected"] = True
    final = clone(fitted[best["name"]]).fit(X, y)  # refit on all labelled history

    now = datetime.now(timezone.utc)
    return {
        "symbol": symbol,
        "horizon": horizon,
        "model": final,
        "feature_cols": FEATURE_COLUMNS,
        "meta": {
            "symbol": symbol,
            "horizon_days": horizon,
            "model": best["name"],
            "model_version": now.strftime("v%Y%m%d%H%M%S"),
            "trained_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "train_samples": int(len(X_tr)),
            "test_samples": int(len(X_te)),
            "baseline_accuracy": baseline,
            "models": scores,
            "top_features": _importances(final, FEATURE_COLUMNS),
        },
    }


def predict_latest(bundle: dict, df: pd.DataFrame) -> dict:
    feats = build_features(df)
    last = feats.iloc[[-1]][bundle["feature_cols"]]
    if last.isna().any(axis=None):
        raise MlError("Latest bar does not have enough history to compute all features")
    model = bundle["model"]
    p_up = float(model.predict_proba(last)[0][list(model.classes_).index(1)])
    meta = bundle["meta"]
    confidence = max(p_up, 1 - p_up)
    best = next(s for s in meta["models"] if s["selected"])
    edge = best["accuracy"] - meta["baseline_accuracy"]
    reliability = "MODERATE" if (edge >= 0.03 and confidence >= 0.55) else "LOW"
    return {
        "direction": "UP" if p_up >= 0.5 else "DOWN",
        "probability_up": p_up,
        "probability_down": 1 - p_up,
        "confidence": confidence,
        "reliability": reliability,
        "as_of_date": df["date"].iloc[-1].date(),
    }
