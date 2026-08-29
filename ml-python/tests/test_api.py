from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_up() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_predict_uses_camel_case_contract() -> None:
    response = client.post(
        "/predict",
        json={"symbol": "vlx", "lastPrice": 187.42, "recentReturns": [0.001, 0.002, 0.004]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["symbol"] == "VLX"
    assert body["direction"] == "UP"
    assert 0.5 <= body["confidence"] <= 0.85
    assert body["horizonSeconds"] == 60
    assert "not investment advice" in body["disclaimer"].lower()


def test_predict_detects_downward_momentum() -> None:
    response = client.post(
        "/predict",
        json={"symbol": "VLX", "lastPrice": 100.0, "recentReturns": [-0.003, -0.004, -0.006]},
    )

    assert response.status_code == 200
    assert response.json()["direction"] == "DOWN"


def test_predict_without_history_is_flat() -> None:
    response = client.post("/predict", json={"symbol": "VLX", "lastPrice": 100.0, "recentReturns": []})

    body = response.json()
    assert body["direction"] == "FLAT"
    assert body["confidence"] == 0.5


def test_invalid_payloads_are_rejected() -> None:
    assert client.post("/predict", json={"symbol": "VLX", "lastPrice": -1}).status_code == 422
    assert client.post("/predict", json={"symbol": "", "lastPrice": 10}).status_code == 422
    assert (
        client.post(
            "/predict",
            json={"symbol": "VLX", "lastPrice": 10, "recentReturns": [0.0] * 500},
        ).status_code
        == 422
    )
