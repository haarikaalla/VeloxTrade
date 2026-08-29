import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Depth, OrderReceipt, OrderRequest, Portfolio, Quote, Signal, Tick } from './models';

/** Typed access to the Spring Boot platform API. */
@Injectable({ providedIn: 'root' })
export class MarketApiService {
  private readonly http = inject(HttpClient);

  quote(): Observable<Quote> {
    return this.http.get<Quote>('/api/market/quote');
  }

  depth(): Observable<Depth> {
    return this.http.get<Depth>('/api/market/depth');
  }

  history(limit = 90): Observable<Tick[]> {
    return this.http.get<Tick[]>('/api/market/history', { params: { limit } });
  }

  signal(): Observable<Signal> {
    return this.http.get<Signal>('/api/market/signal');
  }

  placeOrder(request: OrderRequest): Observable<OrderReceipt> {
    return this.http.post<OrderReceipt>('/api/orders', request);
  }

  orders(limit = 15): Observable<OrderReceipt[]> {
    return this.http.get<OrderReceipt[]>('/api/orders', { params: { limit } });
  }

  portfolio(): Observable<Portfolio> {
    return this.http.get<Portfolio>('/api/portfolio');
  }
}
