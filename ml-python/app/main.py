"""FastAPI analytics service for the VeloxTrade simulated market."""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.model import MAX_WINDOW, predict

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

app = FastAPI(
    title="VeloxTrade Analytics",
    version="1.0.0",
    description="Short-horizon directional signals for the simulated VLX market.",
)


class PredictionRequest(BaseModel):
    symbol: str = Field(min_length=1, max_length=12, pattern=r"^[A-Za-z]{1,12}$")
    last_price: float = Field(gt=0, le=1_000_000, alias="lastPrice")
    recent_returns: list[float] = Field(
        default_factory=list, max_length=MAX_WINDOW, alias="recentReturns"
    )

    model_config = {"populate_by_name": True}


class PredictionResponse(BaseModel):
    symbol: str
    direction: str
    confidence: float
    momentum: float
    volatility: float
    horizon_seconds: int = Field(serialization_alias="horizonSeconds")
    disclaimer: str

    model_config = {"populate_by_name": True}


@app.get("/health", tags=["ops"])
def health() -> dict[str, str]:
    return {"status": "UP", "service": "veloxtrade-analytics"}


@app.post("/predict", response_model=PredictionResponse, response_model_by_alias=True, tags=["analytics"])
def create_prediction(request: PredictionRequest) -> PredictionResponse:
    signal = predict(request.symbol, request.last_price, request.recent_returns)
    return PredictionResponse(
        symbol=signal.symbol,
        direction=signal.direction,
        confidence=signal.confidence,
        momentum=signal.momentum,
        volatility=signal.volatility,
        horizon_seconds=signal.horizon_seconds,
        disclaimer=signal.disclaimer,
    )
