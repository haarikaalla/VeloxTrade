from app.model import predict


def test_momentum_is_weighted_towards_recent_returns() -> None:
    older_down = predict("VLX", 100.0, [-0.01] * 10 + [0.02] * 3)
    assert older_down.direction == "UP"


def test_confidence_is_capped() -> None:
    signal = predict("VLX", 100.0, [0.05] * 30)
    assert signal.confidence <= 0.85


def test_flat_market_has_neutral_confidence() -> None:
    signal = predict("VLX", 100.0, [])
    assert signal.direction == "FLAT"
    assert signal.confidence == 0.5
    assert signal.volatility == 0.0


def test_symbol_is_normalised() -> None:
    assert predict("vlx", 100.0, [0.001]).symbol == "VLX"
