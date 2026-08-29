import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Depth } from '../core/models';

/** Depth-of-market ladder with proportional liquidity bars. */
@Component({
  selector: 'vt-order-book',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <span>Bid size</span>
      <span>Price</span>
      <span>Ask size</span>
    </div>

    @for (row of rows(); track row.index) {
      <div class="row">
        <span class="size bid">
          @if (row.bid) {
            <i class="bar" [style.width.%]="row.bidWidth"></i>
            <b>{{ row.bid.quantity | number }}</b>
          }
        </span>
        <span class="prices">
          <em class="bid-price">{{ row.bid ? (row.bid.price | number: '1.2-2') : '—' }}</em>
          <em class="ask-price">{{ row.ask ? (row.ask.price | number: '1.2-2') : '—' }}</em>
        </span>
        <span class="size ask">
          @if (row.ask) {
            <i class="bar" [style.width.%]="row.askWidth"></i>
            <b>{{ row.ask.quantity | number }}</b>
          }
        </span>
      </div>
    } @empty {
      <p class="empty">Waiting for the matching engine…</p>
    }
  `,
  styles: [
    `
      .head,
      .row {
        display: grid;
        grid-template-columns: 1fr 1.1fr 1fr;
        align-items: center;
        gap: 8px;
      }
      .head {
        font: 500 10px 'DM Mono', monospace;
        letter-spacing: 1.2px;
        text-transform: uppercase;
        color: #7c9b8d;
        padding-bottom: 10px;
        border-bottom: 1px solid #1c3a2e;
      }
      .row {
        padding: 7px 0;
        border-bottom: 1px solid rgba(28, 58, 46, 0.5);
        font: 500 12px 'DM Mono', monospace;
      }
      .size {
        position: relative;
        display: flex;
        align-items: center;
        min-height: 20px;
      }
      .size.bid {
        justify-content: flex-start;
      }
      .size.ask {
        justify-content: flex-end;
      }
      .bar {
        position: absolute;
        top: 2px;
        bottom: 2px;
        border-radius: 2px;
      }
      .bid .bar {
        left: 0;
        background: rgba(103, 240, 162, 0.16);
      }
      .ask .bar {
        right: 0;
        background: rgba(255, 122, 122, 0.16);
      }
      .size b {
        position: relative;
        font-weight: 500;
        color: #cfe6da;
      }
      .prices {
        display: flex;
        justify-content: space-between;
        gap: 10px;
      }
      .bid-price {
        color: #67f0a2;
        font-style: normal;
      }
      .ask-price {
        color: #ff7a7a;
        font-style: normal;
      }
      .empty {
        color: #7c9b8d;
        font: 500 11px 'DM Mono', monospace;
      }
    `,
  ],
})
export class OrderBookComponent {
  readonly depth = input<Depth | null>(null);

  protected readonly rows = computed(() => {
    const book = this.depth();
    if (!book) {
      return [];
    }
    const bids = book.bids ?? [];
    const asks = book.asks ?? [];
    const largest = Math.max(
      1,
      ...bids.map((level) => level.quantity),
      ...asks.map((level) => level.quantity),
    );
    const depthRows = Math.min(8, Math.max(bids.length, asks.length));
    return Array.from({ length: depthRows }, (_, index) => ({
      index,
      bid: bids[index] ?? null,
      ask: asks[index] ?? null,
      bidWidth: ((bids[index]?.quantity ?? 0) / largest) * 100,
      askWidth: ((asks[index]?.quantity ?? 0) / largest) * 100,
    }));
  });
}
