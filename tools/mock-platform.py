"""Dependency-free mock of the Spring Boot platform API.

Purpose: lets you run and demo the Angular dashboard on a machine with no JDK,
Maven, or Docker installed. It mimics the REST contract of `platform-java`
closely enough to drive every panel of the UI.

It is NOT part of the product. It has no auth, no persistence, and no security
hardening -- never run it outside a local demo.

    python tools/mock-platform.py        # listens on http://localhost:8080
"""

from __future__ import annotations

import json
import random
import threading
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

SYMBOL = "VLX"
OPENING_CASH = 100_000.00
PORT = 8080

_lock = threading.Lock()
_state = {
    "price": 187.42,
    "history": [],
    "accounts": {},          # token -> account dict
    "orders": [],
}


def _now_ms() -> int:
    return int(time.time() * 1000)


def _iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _drift() -> None:
    """Random-walk the price once per second and keep a rolling tick history."""
    while True:
        with _lock:
            shock = random.gauss(0, 0.18)
            _state["price"] = max(1.0, round(_state["price"] + shock, 2))
            _state["history"].append({"price": _state["price"], "timestamp": _now_ms()})
            _state["history"] = _state["history"][-180:]
        time.sleep(1)


def _quote() -> dict:
    price = _state["price"]
    return {
        "symbol": SYMBOL,
        "price": price,
        "bid": round(price - 0.03, 2),
        "ask": round(price + 0.03, 2),
        "restingOrders": random.randint(9, 14),
        "timestamp": _now_ms(),
    }


def _depth() -> dict:
    price = _state["price"]
    return {
        "symbol": SYMBOL,
        "bids": [
            {"price": round(price - 0.03 * (i + 1), 2),
             "quantity": random.randint(120, 900)}
            for i in range(6)
        ],
        "asks": [
            {"price": round(price + 0.03 * (i + 1), 2),
             "quantity": random.randint(120, 900)}
            for i in range(6)
        ],
        "timestamp": _now_ms(),
    }


def _signal() -> dict:
    window = [t["price"] for t in _state["history"][-30:]]
    momentum = 0.0
    if len(window) > 1 and window[0]:
        momentum = (window[-1] - window[0]) / window[0]
    direction = "UP" if momentum > 0.0005 else "DOWN" if momentum < -0.0005 else "FLAT"
    return {
        "symbol": SYMBOL,
        "direction": direction,
        "confidence": round(min(0.85, 0.5 + abs(momentum) * 40), 4),
        "momentum": round(momentum, 6),
        "volatility": round(random.uniform(0.0008, 0.004), 6),
        "horizonSeconds": 60,
        "disclaimer": "Simulated analytics. Not investment advice.",
    }


def _portfolio(account: dict) -> dict:
    price = _state["price"]
    positions = []
    value = 0.0
    for symbol, pos in account["positions"].items():
        market_value = round(pos["quantity"] * price, 2)
        value += market_value
        positions.append({
            "symbol": symbol,
            "quantity": pos["quantity"],
            "averagePrice": round(pos["averagePrice"], 2),
            "lastPrice": price,
            "marketValue": market_value,
            "unrealizedPnl": round(market_value - pos["quantity"] * pos["averagePrice"], 2),
        })
    return {
        "displayName": account["displayName"],
        "cashBalance": round(account["cash"], 2),
        "positionsValue": round(value, 2),
        "netLiquidation": round(account["cash"] + value, 2),
        "unrealizedPnl": round(sum(p["unrealizedPnl"] for p in positions), 2),
        "positions": positions,
    }


def _place_order(account: dict, body: dict) -> tuple[int, dict]:
    side = body.get("side")
    quantity = int(body.get("quantity", 0))
    limit_price = float(body.get("limitPrice", 0))
    position = account["positions"].get(SYMBOL, {"quantity": 0, "averagePrice": 0.0})

    if side == "BUY" and quantity * limit_price > account["cash"]:
        return 422, {"timestamp": _iso_now(), "status": 422,
                     "error": "Insufficient buying power.", "details": []}
    if side == "SELL" and quantity > position["quantity"]:
        return 422, {"timestamp": _iso_now(), "status": 422,
                     "error": "Short selling is not supported.", "details": []}

    fill_price = round(_state["price"], 2)
    if side == "BUY":
        total_cost = position["quantity"] * position["averagePrice"] + quantity * fill_price
        position["quantity"] += quantity
        position["averagePrice"] = total_cost / position["quantity"]
        account["cash"] -= quantity * fill_price
    else:
        position["quantity"] -= quantity
        account["cash"] += quantity * fill_price
        if position["quantity"] == 0:
            position["averagePrice"] = 0.0
    account["positions"][SYMBOL] = position

    receipt = {
        "orderId": str(uuid.uuid4()),
        "symbol": SYMBOL,
        "side": side,
        "status": "FILLED",
        "quantity": quantity,
        "filledQuantity": quantity,
        "limitPrice": limit_price,
        "averageFillPrice": fill_price,
        "matchLatencyNanos": random.randint(600, 9000),
        "createdAt": _iso_now(),
    }
    account["orders"].insert(0, receipt)
    return 201, receipt


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # quieter console
        pass

    def _send(self, status: int, payload) -> None:
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _account(self):
        header = self.headers.get("Authorization", "")
        token = header[7:] if header.startswith("Bearer ") else ""
        return _state["accounts"].get(token)

    def do_GET(self):  # noqa: N802
        route = urlparse(self.path)
        query = parse_qs(route.query)
        with _lock:
            if route.path == "/api/market/quote":
                return self._send(200, _quote())
            if route.path == "/api/market/depth":
                return self._send(200, _depth())
            if route.path == "/api/market/history":
                limit = int(query.get("limit", ["90"])[0])
                return self._send(200, _state["history"][-limit:])
            if route.path == "/api/market/signal":
                return self._send(200, _signal())

            account = self._account()
            if route.path in ("/api/portfolio", "/api/orders"):
                if account is None:
                    return self._send(401, {"error": "Unauthorized"})
                if route.path == "/api/portfolio":
                    return self._send(200, _portfolio(account))
                limit = int(query.get("limit", ["20"])[0])
                return self._send(200, account["orders"][:limit])
        self._send(404, {"error": "Not found"})

    def do_POST(self):  # noqa: N802
        length = int(self.headers.get("Content-Length", 0))
        try:
            body = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return self._send(400, {"error": "Malformed JSON", "details": []})

        route = urlparse(self.path)
        with _lock:
            if route.path in ("/api/auth/register", "/api/auth/login"):
                email = str(body.get("email", "")).lower()
                token = str(uuid.uuid4())
                display = body.get("displayName") or email.split("@")[0].title()
                _state["accounts"][token] = {
                    "email": email,
                    "displayName": display,
                    "cash": OPENING_CASH,
                    "positions": {},
                    "orders": [],
                }
                status = 201 if route.path.endswith("register") else 200
                return self._send(status, {
                    "accessToken": token,
                    "tokenType": "Bearer",
                    "expiresInSeconds": 43200,
                    "email": email,
                    "displayName": display,
                })

            if route.path == "/api/orders":
                account = self._account()
                if account is None:
                    return self._send(401, {"error": "Unauthorized"})
                status, payload = _place_order(account, body)
                return self._send(status, payload)
        self._send(404, {"error": "Not found"})


if __name__ == "__main__":
    for _ in range(90):  # pre-seed a chart history so the UI opens populated
        _state["price"] = max(1.0, round(_state["price"] + random.gauss(0, 0.18), 2))
        _state["history"].append({"price": _state["price"], "timestamp": _now_ms()})

    threading.Thread(target=_drift, daemon=True).start()
    print(f"Mock VeloxTrade platform on http://localhost:{PORT} (demo only)")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
