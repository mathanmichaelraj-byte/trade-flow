"""Tiny on-disk model store: one joblib bundle + one JSON sidecar per (symbol, horizon)."""
import json
import re
import threading
from datetime import datetime, timezone

import joblib

from .config import MODEL_DIR, MODEL_TTL_HOURS
from .features import FEATURE_COLUMNS

_locks: dict[str, threading.Lock] = {}
_guard = threading.Lock()


def key(symbol: str, horizon: int) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", symbol).strip(".") or "SYMBOL"
    return f"{safe}_h{horizon}"


def lock_for(k: str) -> threading.Lock:
    with _guard:
        return _locks.setdefault(k, threading.Lock())


def load_fresh(symbol: str, horizon: int):
    """Return the saved bundle if it exists, is recent and matches the current feature set; otherwise None."""
    path = MODEL_DIR / f"{key(symbol, horizon)}.joblib"
    if not path.exists():
        return None
    try:
        bundle = joblib.load(path)  # files are written only by this service
        trained = datetime.strptime(bundle["meta"]["trained_at"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except Exception:
        return None
    age_h = (datetime.now(timezone.utc) - trained).total_seconds() / 3600
    if age_h > MODEL_TTL_HOURS or bundle.get("feature_cols") != FEATURE_COLUMNS:
        return None
    return bundle


def save(bundle: dict) -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    k = key(bundle["symbol"], bundle["horizon"])
    joblib.dump(bundle, MODEL_DIR / f"{k}.joblib")
    (MODEL_DIR / f"{k}.json").write_text(json.dumps(bundle["meta"], indent=2), encoding="utf-8")


def list_meta() -> list[dict]:
    if not MODEL_DIR.exists():
        return []
    out = []
    for p in sorted(MODEL_DIR.glob("*.json")):
        try:
            out.append(json.loads(p.read_text(encoding="utf-8")))
        except Exception:
            continue
    return out
