import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { AuthResponse } from './models';

const STORAGE_KEY = 'veloxtrade.session';

interface StoredSession {
  accessToken: string;
  email: string;
  displayName: string;
  expiresAt: number;
}

/**
 * Holds the signed-in session. The token lives in sessionStorage so it is
 * dropped when the tab closes and is never shared with another origin.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<StoredSession | null>(readStoredSession());

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly displayName = computed(() => this.session()?.displayName ?? '');

  get token(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (current.expiresAt <= Date.now()) {
      this.logout();
      return null;
    }
    return current.accessToken;
  }

  register(email: string, password: string, displayName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { email, password, displayName })
      .pipe(tap((response) => this.store(response)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(tap((response) => this.store(response)));
  }

  logout(): void {
    this.session.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
  }

  private store(response: AuthResponse): void {
    const stored: StoredSession = {
      accessToken: response.accessToken,
      email: response.email,
      displayName: response.displayName,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
    };
    this.session.set(stored);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  }
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as StoredSession;
    return parsed.expiresAt > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}
