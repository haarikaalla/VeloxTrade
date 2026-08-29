import { DestroyRef, Injectable, NgZone, inject, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';

import { Depth, Quote } from './models';

/**
 * STOMP-over-WebSocket connection to the platform. Falls back silently when the
 * socket drops; the component keeps polling REST so the screen never goes blank.
 */
@Injectable({ providedIn: 'root' })
export class MarketStreamService {
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  readonly quote = signal<Quote | null>(null);
  readonly depth = signal<Depth | null>(null);
  readonly connected = signal(false);

  private client?: Client;

  connect(symbol: string): void {
    if (this.client) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.zone.run(() => this.connected.set(true));
        client.subscribe(`/topic/market/${symbol}`, (message: IMessage) =>
          this.zone.run(() => this.quote.set(JSON.parse(message.body) as Quote)),
        );
        client.subscribe(`/topic/depth/${symbol}`, (message: IMessage) =>
          this.zone.run(() => this.depth.set(JSON.parse(message.body) as Depth)),
        );
      },
      onWebSocketClose: () => this.zone.run(() => this.connected.set(false)),
      onStompError: () => this.zone.run(() => this.connected.set(false)),
    });

    this.client = client;
    client.activate();
    this.destroyRef.onDestroy(() => void client.deactivate());
  }
}
