import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppComponent } from './app.component';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

describe('AppComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('renders the terminal shell and requests market data on init', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    httpMock.expectOne((request) => request.url === '/api/market/history').flush([
      { price: 100, timestamp: 1 },
      { price: 102, timestamp: 2 },
    ]);
    httpMock.expectOne('/api/market/quote').flush({
      symbol: 'VLX',
      price: 102,
      bid: 101.9,
      ask: 102.1,
      restingOrders: 8,
      timestamp: 3,
    });
    httpMock.expectOne('/api/market/depth').flush({
      symbol: 'VLX',
      bids: [{ price: 101.9, quantity: 200 }],
      asks: [{ price: 102.1, quantity: 150 }],
      timestamp: 3,
    });
    httpMock.expectOne('/api/market/signal').flush({
      symbol: 'VLX',
      direction: 'UP',
      confidence: 0.62,
      momentum: 0.001,
      volatility: 0.002,
      horizonSeconds: 60,
      disclaimer: 'Simulated analytics.',
    });

    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('VELOX');
    expect(text).toContain('Order book');
    expect(text).toContain('102.00');
  });

  it('shows the auth panel until a session exists', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock.match(() => true).forEach((request) => request.flush(null));
    fixture.detectChanges();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    expect(fixture.nativeElement.querySelector('vt-auth-panel')).toBeTruthy();
  });

  afterEach(() => sessionStorage.clear());
});
