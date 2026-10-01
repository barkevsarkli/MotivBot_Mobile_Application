"""Raw app answers -> dataset columns. Single source of truth for scoring.

Pure functions, no torch. Every function raises ScoringError on invalid input.
"""

from dataclasses import dataclass

from app import config

PHQ9_ITEM_COUNT = 9
PHQ9_ITEM_MAX = 3

RSES_ITEM_COUNT = 10
RSES_ITEM_MAX = 3  # app values: Strongly agree = 3, Agree = 2, Disagree = 1, Strongly disagree = 0
# 1-based item numbers of the negatively worded Rosenberg items (identical order in en/tr/fa).
RSES_NEGATIVE_ITEMS = frozenset({3, 5, 8, 9, 10})


class ScoringError(ValueError):
    pass


@dataclass(frozen=True)
class ModelInputs:
    text: str
    age: int
    sex: float
    rses: int
    phq: int


def clean_text(text: str) -> str:
    cleaned = text.strip()
    if not cleaned:
        raise ScoringError("text must not be empty")
    return cleaned


def validate_age(age: int) -> int:
    if isinstance(age, bool) or not isinstance(age, int):
        raise ScoringError("age must be an integer")
    if not config.AGE_MIN <= age <= config.AGE_MAX:
        raise ScoringError(f"age must be between {config.AGE_MIN} and {config.AGE_MAX}")
    return age


def encode_gender(gender: str) -> float:
    try:
        return float(config.GENDER_MAP[gender])
    except KeyError:
        allowed = ", ".join(sorted(config.GENDER_MAP))
        raise ScoringError(f"gender must be one of: {allowed}") from None


def _check_items(name: str, items: list[int], count: int, max_value: int) -> None:
    if len(items) != count:
        raise ScoringError(f"{name} must contain exactly {count} items, got {len(items)}")
    for i, value in enumerate(items, start=1):
        if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= max_value:
            raise ScoringError(f"{name} item {i} must be an integer between 0 and {max_value}")


def phq9_total(items: list[int]) -> int:
    """Sum of the 9 PHQ-9 items (0-27).

    The app's attention check (qb_10, "Please answer 'Not at all'") must not be sent.
    """
    _check_items("phq9_items", items, PHQ9_ITEM_COUNT, PHQ9_ITEM_MAX)
    return sum(items)


def rses_total(items: list[int]) -> int:
    """Rosenberg total (10-40, higher = better self-esteem) from raw app values (0-3).

    Positively worded items score value + 1, negatively worded items score 4 - value.
    """
    _check_items("rses_items", items, RSES_ITEM_COUNT, RSES_ITEM_MAX)
    return sum(
        (4 - value) if number in RSES_NEGATIVE_ITEMS else (value + 1)
        for number, value in enumerate(items, start=1)
    )


def build_model_inputs(
    text: str, age: int, gender: str, phq9_items: list[int], rses_items: list[int]
) -> ModelInputs:
    return ModelInputs(
        text=clean_text(text),
        age=validate_age(age),
        sex=encode_gender(gender),
        rses=rses_total(rses_items),
        phq=phq9_total(phq9_items),
    )


def metadata_vector(age: float, sex: float, rses: float, phq: float) -> list[float]:
    """Structured features in model order [age, sex, rses, phq], normalised if configured."""
    raw = [float(age), float(sex), float(rses), float(phq)]
    if not config.NORMALIZE_METADATA:
        return raw
    return [(v - m) / s for v, m, s in zip(raw, config.METADATA_MEAN, config.METADATA_STD)]
