"""API tests with the model mocked. Never imports torch."""

import pytest
from fastapi.testclient import TestClient

from app import config
from app.main import create_app

VALID = {
    "text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı",
    "age": 25,
    "gender": "male",
    "phq9_items": [1, 2, 1, 0, 2, 1, 1, 2, 0],
    "rses_items": [2, 2, 1, 2, 1, 2, 2, 1, 1, 1],
    "language": "tr",
}


class FakePredictor:
    def __init__(self, depression=(0.8, 0.1, 0.1)):
        self.depression = list(depression)
        self.calls = []

    def predict_raw(self, text, age, sex, rses, phq):
        self.calls.append((text, age, sex, rses, phq))
        result = {head: [0.05, 0.07, 0.88] for head in config.HEADS}
        result["Depression_Hopelessness_Sadness"] = self.depression
        return result


def make_client(predictor=None, status="ready"):
    app = create_app(loader=None)
    app.state.model.status = status
    app.state.model.predictor = predictor
    return TestClient(app)


@pytest.fixture
def predictor():
    return FakePredictor()


@pytest.fixture
def client(predictor):
    return make_client(predictor)


def test_health_ready(client):
    assert client.get("/health").json() == {"status": "ready", "model_id": config.MODEL_ID}


def test_health_and_predict_while_loading():
    client = make_client(status="loading")
    assert client.get("/health").json()["status"] == "loading"
    response = client.post("/predict", json=VALID)
    assert response.status_code == 503
    assert response.headers["Retry-After"] == "10"


def test_predict_while_load_failed():
    response = make_client(status="error").post("/predict", json=VALID)
    assert response.status_code == 503


def test_predict_shape(client, predictor):
    response = client.post("/predict", json=VALID)
    assert response.status_code == 200
    body = response.json()
    assert body["model_id"] == config.MODEL_ID
    assert body["inputs_used"] == {"Age": 25, "sex": 0.0, "PHQ-9": 10, "Rosenberg.self-esteem": 30}
    assert [d["key"] for d in body["dimensions"]] == config.HEADS
    anxiety = body["dimensions"][0]
    assert anxiety["level"] == "High"
    assert anxiety["probabilities"] == {"No": 0.05, "Low": 0.07, "High": 0.88}
    assert body["dimensions"][1]["level"] == "No"
    assert body["elevated_risk"] is False
    assert predictor.calls == [(VALID["text"], 25, 0.0, 30, 10)]


def test_elevated_risk_from_phq_item_9(client):
    body = client.post("/predict", json={**VALID, "phq9_items": [0, 0, 0, 0, 0, 0, 0, 0, 1]}).json()
    assert body["elevated_risk"] is True


def test_elevated_risk_from_depression_high():
    client = make_client(FakePredictor(depression=(0.1, 0.2, 0.7)))
    assert client.post("/predict", json=VALID).json()["elevated_risk"] is True


@pytest.mark.parametrize(
    "patch, fragment",
    [
        ({"text": "   "}, "text"),
        ({"age": 9}, "age"),
        ({"age": "25"}, "age"),
        ({"gender": "other"}, "gender"),
        ({"phq9_items": [0] * 10}, "phq9_items"),
        ({"phq9_items": [0] * 8 + [4]}, "phq9_items"),
        ({"rses_items": [0] * 9}, "rses_items"),
        ({"rses_items": [0] * 9 + [5]}, "rses_items"),
        ({"unexpected": 1}, "unexpected"),
    ],
)
def test_invalid_input_returns_422(client, patch, fragment):
    response = client.post("/predict", json={**VALID, **patch})
    assert response.status_code == 422
    detail = response.json()["detail"]
    assert isinstance(detail, str) and fragment in detail


def test_missing_field_returns_422(client):
    body = {k: v for k, v in VALID.items() if k != "age"}
    response = client.post("/predict", json=body)
    assert response.status_code == 422
    assert "age" in response.json()["detail"]


def test_validation_error_does_not_echo_text(client):
    response = client.post("/predict", json={**VALID, "age": 5})
    assert VALID["text"] not in response.text
