import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Tick } from '../core/models';

/** Lightweight SVG price chart: area fill, sparkline and gridlines. */
@Component({
  selector: 'vt-price-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="chart" viewBox="0 0 720 220" preserveAspectRatio="none" role="img"
         aria-label="Simulated VLX price history">
      @for (line of gridLines; track line) {
        <line class="grid" x1="0" [attr.y1]="line" x2="720" [attr.y2]="line" />
      }
      @if (points().length > 1) {
        <polygon class="area" [attr.points]="areaPoints()" />
        <polyline class="line" [class.negative]="!rising()" [attr.points]="linePoints()" />
        <circle class="marker" [attr.cx]="lastPoint().x" [attr.cy]="lastPoint().y" r="4" />
      } @else {
        <text class="empty" x="360" y="110" text-anchor="middle">Awaiting market data…</text>
      }
    </svg>
  `,
  styles: [
    `
      .chart {
        width: 100%;
        height: 220px;
        display: block;
      }
      .grid {
        stroke: rgba(103, 240, 162, 0.08);
        stroke-width: 1;
      }
      .line {
        fill: none;
        stroke: #67f0a2;
        stroke-width: 2.5;
        stroke-linejoin: round;
        stroke-linecap: round;
      }
      .line.negative {
        stroke: #ff7a7a;
      }
      .area {
        fill: rgba(103, 240, 162, 0.12);
      }
      .marker {
        fill: #67f0a2;
      }
      .empty {
        fill: #6f8d7f;
        font: 500 13px 'DM Mono', monospace;
      }
    `,
  ],
})
export class PriceChartComponent {
  readonly ticks = input.required<Tick[]>();

  protected readonly gridLines = [20, 70, 120, 170];

  protected readonly points = computed(() => {
    const series = this.ticks();
    if (series.length < 2) {
      return [];
    }
    const prices = series.map((tick) => tick.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    return series.map((tick, index) => ({
      x: (index / (series.length - 1)) * 720,
      y: 200 - ((tick.price - min) / range) * 180,
    }));
  });

  protected readonly linePoints = computed(() =>
    this.points()
      .map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`)
      .join(' '),
  );

  protected readonly areaPoints = computed(() => `0,220 ${this.linePoints()} 720,220`);

  protected readonly lastPoint = computed(
    () => this.points().at(-1) ?? { x: 0, y: 0 },
  );

  protected readonly rising = computed(() => {
    const series = this.ticks();
    return series.length < 2 || series[series.length - 1].price >= series[0].price;
  });
}
