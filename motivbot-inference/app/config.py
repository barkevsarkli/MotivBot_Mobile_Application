"""All tunables for the inference service. Every value can be overridden by an env var."""

import json
import os


def _env_float(name: str, default: float) -> float:
    return float(os.environ.get(name, default))


def _env_int(name: str, default: int) -> int:
    return int(os.environ.get(name, default))


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _env_json(name: str, default):
    raw = os.environ.get(name)
    return json.loads(raw) if raw else default


MODEL_ID: str = os.environ.get("MOTIVBOT_MODEL_ID", "amirbhat/motivbot-multilingual-XLMR-10062026v3")

# Head order and class names exactly as in the reference notebook.
HEADS: list[str] = [
    "Anxiety_Fear_Worry",
    "Depression_Hopelessness_Sadness",
    "Self_Esteem_Issue",
    "Positive_Affect",
    "Negative_Affect",
]
LEVELS: list[str] = ["No", "Low", "High"]

MAX_LEN: int = _env_int("MOTIVBOT_MAX_LEN", 256)
TEMPERATURE: float = _env_float("MOTIVBOT_TEMPERATURE", 0.45)

# Confirmed by the model author (Amir): male = 0, female = 1, and metadata is used
# raw (no normalisation). "unspecified" = 0.5 is still our own assumption; training
# data only had 0 and 1. Change these only here.
GENDER_MAP: dict[str, float] = _env_json(
    "MOTIVBOT_GENDER_MAP", {"male": 0.0, "female": 1.0, "unspecified": 0.5}
)
# False = raw values, as used in training (confirmed). Kept only for experiments:
# when True, each metadata feature [age, sex, rses, phq] becomes (value - mean) / std.
NORMALIZE_METADATA: bool = _env_bool("MOTIVBOT_NORMALIZE_METADATA", False)
# Placeholders: only used when NORMALIZE_METADATA is True.
METADATA_MEAN: list[float] = _env_json("MOTIVBOT_METADATA_MEAN", [0.0, 0.0, 0.0, 0.0])
METADATA_STD: list[float] = _env_json("MOTIVBOT_METADATA_STD", [1.0, 1.0, 1.0, 1.0])

AGE_MIN: int = 10
AGE_MAX: int = 100

# Comma-separated list of allowed CORS origins. Local development only.
CORS_ORIGINS: list[str] = [
    o.strip()
    for o in os.environ.get(
        "MOTIVBOT_CORS_ORIGINS", "http://localhost,http://localhost:8000,http://127.0.0.1:8000"
    ).split(",")
    if o.strip()
]
