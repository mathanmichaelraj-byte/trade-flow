import datetime as dt
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class Bar(BaseModel):
    date: dt.date
    open: float
    high: float
    low: float
    close: float
    volume: float = 0


class PredictRequest(BaseModel):
    symbol: str = Field(min_length=1, max_length=20)
    horizon_days: int = Field(5, ge=1, le=60)
    bars: list[Bar] = Field(min_length=60, max_length=5000)
    force_retrain: bool = False


class ModelScore(BaseModel):
    name: str
    accuracy: float
    precision: float
    recall: float
    f1: float
    roc_auc: Optional[float] = None
    selected: bool = False


class Feature(BaseModel):
    name: str
    importance: float


class PredictResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    symbol: str
    horizon_days: int
    direction: str
    probability_up: float
    probability_down: float
    confidence: float
    model: str
    model_version: str
    trained_at: str
    trained_now: bool
    as_of_date: dt.date
    train_samples: int
    test_samples: int
    baseline_accuracy: float
    reliability: str
    models: list[ModelScore]
    top_features: list[Feature]


class TrainResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    symbol: str
    horizon_days: int
    model: str
    model_version: str
    trained_at: str
    train_samples: int
    test_samples: int
    baseline_accuracy: float
    models: list[ModelScore]
