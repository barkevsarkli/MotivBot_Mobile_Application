"""Golden test against the reference notebook output (cell 9). Needs the model download."""

import pytest

pytestmark = pytest.mark.model

TEXT = "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı"

# head -> (level, notebook probability in %)
EXPECTED = {
    "Anxiety_Fear_Worry": ("High", 88.6),
    "Depression_Hopelessness_Sadness": ("No", 84.5),
    "Self_Esteem_Issue": ("No", 93.6),
    "Positive_Affect": ("No", 48.3),
    "Negative_Affect": ("High", 38.5),
}
TOLERANCE_PP = 0.5


@pytest.fixture(scope="module")
def predictor():
    from app import config
    from app.model import Predictor

    assert not config.NORMALIZE_METADATA, "golden values were produced with raw metadata"
    return Predictor()


def test_matches_notebook(predictor):
    from app import config

    result = predictor.predict_raw(TEXT, age=25, sex=0, rses=18, phq=12)
    assert list(result) == config.HEADS
    for head, (level, expected_pct) in EXPECTED.items():
        probs = result[head]
        assert config.LEVELS[probs.index(max(probs))] == level, head
        actual_pct = probs[config.LEVELS.index(level)] * 100
        assert abs(actual_pct - expected_pct) <= TOLERANCE_PP, f"{head}: {actual_pct:.2f}% vs {expected_pct}%"
