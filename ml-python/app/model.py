"""Signal model for the VeloxTrade simulated market.

The model is intentionally transparent: an exponentially weighted momentum
estimate combined with realised volatility. It produces a directional call plus a
calibrated-looking confidence so the dashboard has something meaningful to show
without pretending to be a trained production forecaster.
"""

from __future__ import annotations

from dataclasses import dataclass
from math import exp, sqrt

MAX_WINDOW = 120
DEFAULT_HORIZON_SECONDS = 60
DISCLAIMER = "Simulated analytics for a synthetic market. Not investment advice."


@dataclass(frozen=True)
class Signal:
    symbol: str
    direction: str
    confidence: float
    momentum: float
    volatility: float
    horizon_seconds: int
    disclaimer: str


def _ewma(values: list[float], half_life: float) -> float:
    if not values:
        return 0.0
    decay = 0.5 ** (1.0 / half_life)
    weighted_sum = 0.0
    weight_total = 0.0
    weight = 1.0
    for value in reversed(values):
        weighted_sum += weight * value
        weight_total += weight
        weight *= decay
    return weighted_sum / weight_total if weight_total else 0.0


def _volatility(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / (len(values) - 1)
    return sqrt(variance)


def predict(symbol: str, last_price: float, recent_returns: list[float]) -> Signal:
    """Return the short-horizon directional signal for ``symbol``."""
    window = [float(value) for value in recent_returns[-MAX_WINDOW:]]
    momentum = _ewma(window, half_life=8.0)
    volatility = _volatility(window)

    # Scale momentum by realised volatility so a calm market needs a smaller move
    # to be considered directional, then squash into a probability-like number.
    scaled = momentum / (volatility + 1e-6)
    probability_up = 1.0 / (1.0 + exp(-4.0 * scaled))
    direction = "UP" if probability_up >= 0.5 else "DOWN"
    confidence = max(probability_up, 1.0 - probability_up)

    # Never claim more certainty than a toy model can support.
    confidence = min(confidence, 0.85) if window else 0.5

    return Signal(
        symbol=symbol.upper(),
        direction=direction if window else "FLAT",
        confidence=round(confidence, 4),
        momentum=round(momentum, 8),
        volatility=round(volatility, 8),
        horizon_seconds=DEFAULT_HORIZON_SECONDS,
        disclaimer=DISCLAIMER,
    )
