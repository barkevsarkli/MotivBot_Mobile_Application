import pytest

from app import config
from app.scoring import (
    ScoringError,
    build_model_inputs,
    encode_gender,
    metadata_vector,
    phq9_total,
    rses_total,
    validate_age,
)

# Raw app values for RSES: Strongly agree = 3, Agree = 2, Disagree = 1, Strongly disagree = 0.
SA, A, D, SD = 3, 2, 1, 0
POSITIVE = {1, 2, 4, 6, 7}


def rses_pattern(positive_value: int, negative_value: int) -> list[int]:
    return [positive_value if i in POSITIVE else negative_value for i in range(1, 11)]


class TestPhq9:
    def test_sums_nine_items(self):
        assert phq9_total([1, 2, 1, 0, 2, 1, 1, 2, 0]) == 10
        assert phq9_total([3] * 9) == 27
        assert phq9_total([0] * 9) == 0

    def test_attention_check_must_not_be_included(self):
        # Ten items means the attention check (qb_10) was sent along. Reject it.
        with pytest.raises(ScoringError, match="exactly 9"):
            phq9_total([0] * 10)

    @pytest.mark.parametrize("items", [[0] * 8, [], [0] * 8 + [4], [0] * 8 + [-1]])
    def test_rejects_wrong_count_or_range(self, items):
        with pytest.raises(ScoringError):
            phq9_total(items)


class TestRses:
    def test_all_strongly_agree(self):
        assert rses_total([SA] * 10) == 25  # 5 x 4 + 5 x 1

    def test_all_strongly_disagree(self):
        assert rses_total([SD] * 10) == 25  # 5 x 1 + 5 x 4

    def test_best_case(self):
        assert rses_total(rses_pattern(SA, SD)) == 40

    def test_worst_case(self):
        assert rses_total(rses_pattern(SD, SA)) == 10

    def test_mixed(self):
        # positives: Agree -> 3 each (15); negatives: Disagree -> 3 each (15)
        assert rses_total(rses_pattern(A, D)) == 30

    @pytest.mark.parametrize("items", [[SA] * 9, [SA] * 11, [SA] * 9 + [4], [SA] * 9 + [-1]])
    def test_rejects_wrong_count_or_range(self, items):
        with pytest.raises(ScoringError):
            rses_total(items)


class TestGenderAndAge:
    def test_gender_mapping(self):
        assert encode_gender("male") == 0.0
        assert encode_gender("female") == 1.0
        assert encode_gender("unspecified") == 0.5

    def test_unknown_gender(self):
        with pytest.raises(ScoringError):
            encode_gender("other")

    @pytest.mark.parametrize("age", [10, 25, 100])
    def test_valid_age(self, age):
        assert validate_age(age) == age

    @pytest.mark.parametrize("age", [9, 101, -1, True])
    def test_invalid_age(self, age):
        with pytest.raises(ScoringError):
            validate_age(age)


def test_build_model_inputs_trims_text():
    inputs = build_model_inputs("  hello  ", 25, "male", [1, 2, 1, 0, 2, 1, 1, 2, 0], [2, 2, 1, 2, 1, 2, 2, 1, 1, 1])
    assert inputs.text == "hello"
    assert (inputs.age, inputs.sex, inputs.phq, inputs.rses) == (25, 0.0, 10, 30)


def test_empty_text_rejected():
    with pytest.raises(ScoringError):
        build_model_inputs("   \n", 25, "male", [0] * 9, [0] * 10)


def test_metadata_raw_by_default():
    assert not config.NORMALIZE_METADATA
    assert metadata_vector(25, 0, 18, 12) == [25.0, 0.0, 18.0, 12.0]


def test_metadata_normalised_when_enabled(monkeypatch):
    monkeypatch.setattr(config, "NORMALIZE_METADATA", True)
    monkeypatch.setattr(config, "METADATA_MEAN", [20.0, 0.5, 25.0, 10.0])
    monkeypatch.setattr(config, "METADATA_STD", [5.0, 0.5, 5.0, 2.0])
    assert metadata_vector(25, 0, 18, 12) == [1.0, -1.0, -1.4, 1.0]
