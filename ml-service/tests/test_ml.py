import os
import sys
import tempfile
from pathlib import Path

os.environ["MODEL_DIR"] = tempfile.mkdtemp()
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pandas as pd
from fastapi.testclient import TestClient

from app.features import FEATURE_COLUMNS, bars_to_frame, build_features, make_labels
from app.main import app


def synthetic_bars(n=600, seed=1):
    rng = np.random.default_rng(seed)
    regime = np.zeros(n)
    for i in range(1, n):
        regime[i] = 0.95 * regime[i - 1] + rng.normal(0, 0.0008)
    close = 100 * np.exp(np.cumsum(regime + rng.normal(0.0002, 0.012, n)))
    dates = pd.bdate_range("2022-01-03", periods=n)
    rows = []
    for i, d in enumerate(dates):
        o = close[i - 1] if i else close[0]
        c = close[i]
        rows.append({"date": d.date().isoformat(), "open": o, "high": max(o, c) * 1.004,
                     "low": min(o, c) * 0.996, "close": c, "volume": 1_000_000 + int(rng.integers(0, 200_000))})
    return rows


def test_features_have_no_lookahead():
    rows = synthetic_bars()
    df = bars_to_frame(rows)
    base = build_features(df)
    altered = df.copy()
    altered.loc[400:, ["open", "high", "low", "close"]] *= 3  # change only the future
    changed = build_features(altered)
    pd.testing.assert_frame_equal(base.iloc[:400], changed.iloc[:400])


def test_labels_last_rows_are_nan():
    df = bars_to_frame(synthetic_bars())
    y = make_labels(df["close"], 5)
    assert y.iloc[-5:].isna().all()
    assert y.iloc[:-5].notna().all()


def test_feature_columns_complete():
    f = build_features(bars_to_frame(synthetic_bars()))
    assert list(f.columns) == FEATURE_COLUMNS
    assert f.iloc[-1].notna().all()


def test_predict_endpoint_and_model_reuse():
    client = TestClient(app)
    body = {"symbol": "TEST", "horizon_days": 5, "bars": synthetic_bars()}
    r = client.post("/predict", json=body)
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["direction"] in ("UP", "DOWN")
    assert abs(j["probability_up"] + j["probability_down"] - 1) < 1e-9
    assert j["trained_now"] is True
    assert sum(1 for m in j["models"] if m["selected"]) == 1
    assert len(j["models"]) >= 2

    r2 = client.post("/predict", json=body)
    assert r2.json()["trained_now"] is False
    assert r2.json()["model_version"] == j["model_version"]

    r3 = client.post("/predict", json={**body, "force_retrain": True})
    assert r3.json()["trained_now"] is True


def test_rejects_too_little_history():
    client = TestClient(app)
    r = client.post("/predict", json={"symbol": "TINY", "horizon_days": 5, "bars": synthetic_bars(100)})
    assert r.status_code == 400


def test_health():
    assert TestClient(app).get("/health").json()["status"] == "ok"
