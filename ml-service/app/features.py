"""Preprocessing and feature engineering. Every feature at row t uses only data up to and including t."""
import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "ret_1", "ret_2", "ret_5", "ret_10", "ret_20",
    "sma5_20", "price_sma20", "price_sma50", "ema12_26",
    "macd_hist", "rsi_14", "bb_pctb", "bb_width", "atr_pct",
    "vol_20", "vol_ratio", "hl_range", "gap",
]


def bars_to_frame(bars: list[dict]) -> pd.DataFrame:
    """Clean raw bars: numeric coercion, sort, drop duplicate dates and non-positive prices."""
    df = pd.DataFrame(bars)
    for col in ("open", "high", "low", "close", "volume"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df["date"] = pd.to_datetime(df["date"])
    df = df.dropna(subset=["open", "high", "low", "close"])
    df = df[(df[["open", "high", "low", "close"]] > 0).all(axis=1)]
    df["volume"] = df["volume"].fillna(0).clip(lower=0)
    df = df.drop_duplicates("date", keep="last").sort_values("date").reset_index(drop=True)
    return df


def _rsi(close: pd.Series, n: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    avg_loss = loss.ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    with np.errstate(divide="ignore", invalid="ignore"):
        rs = avg_gain / avg_loss
    return 100 - 100 / (1 + rs)


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    c, h, l, o, v = df["close"], df["high"], df["low"], df["open"], df["volume"]
    f = pd.DataFrame(index=df.index)
    ret1 = c.pct_change()
    for n in (1, 2, 5, 10, 20):
        f[f"ret_{n}"] = c.pct_change(n)

    sma5, sma20, sma50 = c.rolling(5).mean(), c.rolling(20).mean(), c.rolling(50).mean()
    f["sma5_20"] = sma5 / sma20 - 1
    f["price_sma20"] = c / sma20 - 1
    f["price_sma50"] = c / sma50 - 1

    ema12, ema26 = c.ewm(span=12, adjust=False).mean(), c.ewm(span=26, adjust=False).mean()
    f["ema12_26"] = ema12 / ema26 - 1
    macd = ema12 - ema26
    f["macd_hist"] = (macd - macd.ewm(span=9, adjust=False).mean()) / c

    f["rsi_14"] = _rsi(c)

    std20 = c.rolling(20).std(ddof=0)
    upper, lower = sma20 + 2 * std20, sma20 - 2 * std20
    width = (upper - lower).replace(0, np.nan)
    f["bb_pctb"] = (c - lower) / width
    f["bb_width"] = width / sma20

    prev_c = c.shift(1)
    tr = pd.concat([h - l, (h - prev_c).abs(), (l - prev_c).abs()], axis=1).max(axis=1)
    f["atr_pct"] = tr.ewm(alpha=1 / 14, adjust=False, min_periods=14).mean() / c

    f["vol_20"] = ret1.rolling(20).std()
    f["vol_ratio"] = np.log((v + 1) / (v.rolling(20).mean() + 1))
    f["hl_range"] = (h - l) / c
    f["gap"] = o / prev_c - 1
    return f[FEATURE_COLUMNS].replace([np.inf, -np.inf], np.nan)


def make_labels(close: pd.Series, horizon: int) -> pd.Series:
    """1 if the close `horizon` trading days ahead is above today's close, else 0. NaN for the last `horizon` rows."""
    fwd = close.shift(-horizon) / close - 1
    return (fwd > 0).astype(float).where(fwd.notna())
