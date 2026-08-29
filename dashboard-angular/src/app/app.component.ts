import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { interval } from 'rxjs';

import { AuthPanelComponent } from './components/auth-panel.component';
import { OrderBookComponent } from './components/order-book.component';
import { PriceChartComponent } from './components/price-chart.component';
import { AuthService } from './core/auth.service';
import { MarketApiService } from './core/market-api.service';
import { MarketStreamService } from './core/market-stream.service';
import { Depth, OrderReceipt, Portfolio, Quote, Side, Signal, Tick } from './core/models';

const SYMBOL = 'VLX';
const MAX_TICKS = 90;

@Component({
  selector: 'vt-root',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
    PriceChartComponent,
    OrderBookComponent,
    AuthPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  private readonly api = inject(MarketApiService);
  private readonly stream = inject(MarketStreamService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly auth = inject(AuthService);
  protected readonly symbol = SYMBOL;

  protected readonly quote = signal<Quote | null>(null);
  protected readonly depth = signal<Depth | null>(null);
  protected readonly ticks = signal<Tick[]>([]);
  protected readonly signalView = signal<Signal | null>(null);
  protected readonly portfolio = signal<Portfolio | null>(null);
  protected readonly orders = signal<OrderReceipt[]>([]);
  protected readonly notice = signal('');
  protected readonly noticeTone = signal<'ok' | 'error'>('ok');
  protected readonly submitting = signal(false);
  protected readonly platformOnline = signal(true);
  protected readonly streaming = this.stream.connected;

  protected readonly orderForm = this.formBuilder.nonNullable.group({
    side: ['BUY' as Side, Validators.required],
    quantity: [10, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
    limitPrice: [0, [Validators.required, Validators.min(0.01)]],
  });

  /** Session change since the page loaded, derived from the tick window. */
  protected readonly changePercent = computed(() => {
    const series = this.ticks();
    if (series.length < 2) {
      return 0;
    }
    const first = series[0].price;
    return first > 0 ? series[series.length - 1].price / first - 1 : 0;
  });

  protected readonly spread = computed(() => {
    const current = this.quote();
    return current ? current.ask - current.bid : 0;
  });

  constructor() {
    // Mirror live stream pushes into the local view state.
    effect(() => {
      const pushed = this.stream.quote();
      if (pushed) {
        this.applyQuote(pushed);
      }
    });
    effect(() => {
      const pushed = this.stream.depth();
      if (pushed) {
        this.depth.set(pushed);
      }
    });
  }

  ngOnInit(): void {
    this.loadHistory();
    this.refreshMarket();
    this.stream.connect(SYMBOL);

    // REST poll as a safety net so the screen still updates without a socket.
    interval(2000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.streaming()) {
          this.refreshMarket();
        }
      });

    interval(10_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadSignal());

    this.loadSignal();
    this.loadAccountViews();
  }

  protected setSide(side: Side): void {
    this.orderForm.patchValue({ side });
  }

  protected useMarketPrice(): void {
    const current = this.quote();
    if (!current) {
      return;
    }
    const side = this.orderForm.getRawValue().side;
    this.orderForm.patchValue({
      limitPrice: Number((side === 'BUY' ? current.ask : current.bid).toFixed(2)),
    });
  }

  protected submitOrder(): void {
    if (this.orderForm.invalid || this.submitting()) {
      return;
    }
    const { side, quantity, limitPrice } = this.orderForm.getRawValue();
    this.submitting.set(true);

    this.api.placeOrder({ symbol: SYMBOL, side, quantity, limitPrice }).subscribe({
      next: (receipt) => {
        this.submitting.set(false);
        this.noticeTone.set('ok');
        this.notice.set(
          `${receipt.side} ${receipt.quantity} ${receipt.symbol} · ${receipt.status} · ` +
            `${receipt.filledQuantity} filled in ${this.latencyLabel(receipt.matchLatencyNanos)}`,
        );
        this.loadAccountViews();
      },
      error: (failure: { error?: { error?: string } }) => {
        this.submitting.set(false);
        this.noticeTone.set('error');
        this.notice.set(failure.error?.error ?? 'Order could not be routed.');
      },
    });
  }

  protected logout(): void {
    this.auth.logout();
    this.portfolio.set(null);
    this.orders.set([]);
    this.notice.set('');
  }

  protected latencyLabel(nanos: number | null): string {
    if (nanos === null) {
      return 'n/a';
    }
    return nanos < 1000 ? `${nanos} ns` : `${(nanos / 1000).toFixed(1)} µs`;
  }

  private refreshMarket(): void {
    this.api.quote().subscribe({
      next: (quote) => {
        this.platformOnline.set(true);
        this.applyQuote(quote);
      },
      error: () => this.platformOnline.set(false),
    });
    this.api.depth().subscribe({
      next: (depth) => this.depth.set(depth ?? null),
      error: () => undefined,
    });
  }

  private applyQuote(quote: Quote): void {
    if (!quote || !Number.isFinite(quote.price)) {
      return;
    }
    this.quote.set(quote);
    this.ticks.update((series) => {
      const next = [...series, { price: quote.price, timestamp: quote.timestamp }];
      return next.length > MAX_TICKS ? next.slice(next.length - MAX_TICKS) : next;
    });
    if (this.orderForm.getRawValue().limitPrice === 0) {
      this.orderForm.patchValue({ limitPrice: Number(quote.price.toFixed(2)) });
    }
  }

  private loadHistory(): void {
    this.api.history(MAX_TICKS).subscribe({
      next: (ticks) => this.ticks.set(ticks ?? []),
      error: () => undefined,
    });
  }

  private loadSignal(): void {
    this.api.signal().subscribe({
      next: (signalView) => this.signalView.set(signalView),
      error: () => this.signalView.set(null),
    });
  }

  private loadAccountViews(): void {
    if (!this.auth.isAuthenticated()) {
      return;
    }
    this.api.portfolio().subscribe({
      next: (portfolio) => this.portfolio.set(portfolio),
      error: () => undefined,
    });
    this.api.orders().subscribe({
      next: (orders) => this.orders.set(orders),
      error: () => undefined,
    });
  }
}
