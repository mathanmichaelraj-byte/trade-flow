import os
from pathlib import Path

MODEL_DIR = Path(os.getenv("MODEL_DIR", "./models")).resolve()
# Re-use a trained model for this long before retraining on fresher data.
MODEL_TTL_HOURS = float(os.getenv("MODEL_TTL_HOURS", "24"))
MIN_BARS = int(os.getenv("MIN_BARS", "250"))
MIN_LABELED = int(os.getenv("MIN_LABELED", "150"))
RANDOM_STATE = 42
VERSION = "1.0.0"
