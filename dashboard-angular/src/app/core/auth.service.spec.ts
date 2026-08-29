import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { MarketApiService } from './market-api.service';

describe('AuthService + authInterceptor', () => {
  let auth: AuthService;
  let api: MarketApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    api = TestBed.inject(MarketApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  function signIn(expiresInSeconds = 3600): void {
    auth.login('trader@example.com', 'correct-horse-battery').subscribe();
    httpMock.expectOne('/api/auth/login').flush({
      accessToken: 'token-123',
      tokenType: 'Bearer',
      expiresInSeconds,
      email: 'trader@example.com',
      displayName: 'Demo Trader',
    });
  }

  it('stores the session after a successful login', () => {
    signIn();
    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.displayName()).toBe('Demo Trader');
  });

  it('attaches the bearer token to protected calls only', () => {
    signIn();

    api.portfolio().subscribe();
    expect(httpMock.expectOne('/api/portfolio').request.headers.get('Authorization'))
      .toBe('Bearer token-123');

    api.quote().subscribe();
    httpMock.expectOne('/api/market/quote').flush({});
  });

  it('drops an expired token instead of sending it', () => {
    signIn(-1);
    expect(auth.token).toBeNull();
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('clears the session when the API returns 401', () => {
    signIn();
    api.portfolio().subscribe({ error: () => undefined });
    httpMock.expectOne('/api/portfolio').flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBeFalse();
  });
});
