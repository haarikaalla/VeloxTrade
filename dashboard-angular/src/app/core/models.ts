/** Shared API contract types mirroring the Spring Boot DTOs. */

export type Side = 'BUY' | 'SELL';

export interface Quote {
  symbol: string;
  price: number;
  bid: number;
  ask: number;
  restingOrders: number;
  timestamp: number;
}

export interface DepthLevel {
  price: number;
  quantity: number;
}

export interface Depth {
  symbol: string;
  bids: DepthLevel[];
  asks: DepthLevel[];
  timestamp: number;
}

export interface Tick {
  price: number;
  timestamp: number;
}

export interface Signal {
  symbol: string;
  direction: 'UP' | 'DOWN' | 'FLAT';
  confidence: number;
  momentum: number;
  volatility: number;
  horizonSeconds: number;
  disclaimer: string;
}

export interface OrderRequest {
  symbol: string;
  side: Side;
  quantity: number;
  limitPrice: number;
}

export interface OrderReceipt {
  orderId: string;
  symbol: string;
  side: Side;
  status: 'FILLED' | 'PARTIALLY_FILLED' | 'RESTING' | 'REJECTED';
  quantity: number;
  filledQuantity: number;
  limitPrice: number;
  averageFillPrice: number | null;
  matchLatencyNanos: number | null;
  createdAt: string;
}

export interface PositionView {
  symbol: string;
  quantity: number;
  averagePrice: number;
  lastPrice: number;
  marketValue: number;
  unrealizedPnl: number;
}

export interface Portfolio {
  displayName: string;
  cashBalance: number;
  positionsValue: number;
  netLiquidation: number;
  unrealizedPnl: number;
  positions: PositionView[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  email: string;
  displayName: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  details: string[];
}
