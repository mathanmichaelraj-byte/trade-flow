import logging

from fastapi import FastAPI, HTTPException

from . import store, training
from .config import MIN_BARS, VERSION
from .features import bars_to_frame
from .schemas import PredictRequest, PredictResponse, TrainResponse
from .training import MlError

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("stocksense.ml")

app = FastAPI(title="StockSense ML Service", version=VERSION,
              description="Trains, evaluates and serves stock direction classifiers.")


def _frame(req: PredictRequest):
    df = bars_to_frame([b.model_dump() for b in req.bars])
    if len(df) < MIN_BARS:
        raise HTTPException(status_code=400, detail=f"At least {MIN_BARS} clean daily bars are required (got {len(df)})")
    return df


def _bundle(req: PredictRequest, df, force: bool):
    """Return (bundle, trained_now). Training for one symbol/horizon is serialized by a lock."""
    k = store.key(req.symbol, req.horizon_days)
    with store.lock_for(k):
        if not force:
            cached = store.load_fresh(req.symbol, req.horizon_days)
            if cached is not None:
                return cached, False
        try:
            bundle = training.train(req.symbol, req.horizon_days, df)
        except MlError as exc:
            raise HTTPException(status_code=400, detail=str(exc))
        store.save(bundle)
        log.info("trained %s h=%d -> %s", req.symbol, req.horizon_days, bundle["meta"]["model"])
        return bundle, True


@app.get("/health")
def health():
    return {"status": "ok", "version": VERSION, "xgboost": training.HAS_XGBOOST}


@app.post("/train", response_model=TrainResponse)
def train(req: PredictRequest):
    df = _frame(req)
    bundle, _ = _bundle(req, df, force=True)
    m = bundle["meta"]
    return TrainResponse(symbol=req.symbol, horizon_days=req.horizon_days, model=m["model"],
                         model_version=m["model_version"], trained_at=m["trained_at"],
                         train_samples=m["train_samples"], test_samples=m["test_samples"],
                         baseline_accuracy=m["baseline_accuracy"], models=m["models"])


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    df = _frame(req)
    bundle, trained_now = _bundle(req, df, force=req.force_retrain)
    try:
        p = training.predict_latest(bundle, df)
    except MlError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    m = bundle["meta"]
    return PredictResponse(
        symbol=req.symbol, horizon_days=req.horizon_days, model=m["model"], model_version=m["model_version"],
        trained_at=m["trained_at"], trained_now=trained_now, train_samples=m["train_samples"],
        test_samples=m["test_samples"], baseline_accuracy=m["baseline_accuracy"], models=m["models"],
        top_features=m["top_features"], **p)


@app.get("/models")
def models():
    return store.list_meta()
