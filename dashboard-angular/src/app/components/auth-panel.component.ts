import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../core/auth.service';

/** Sign-in / sign-up panel shown until an account session exists. */
@Component({
  selector: 'vt-auth-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <h2>{{ mode() === 'login' ? 'Sign in to trade' : 'Create a simulated account' }}</h2>
      <p class="hint">
        Accounts are local to this simulation and start with $100,000 of practice cash.
      </p>

      <form [formGroup]="form" (ngSubmit)="submit()">
        @if (mode() === 'register') {
          <label>
            Display name
            <input formControlName="displayName" autocomplete="nickname" maxlength="80" />
          </label>
        }
        <label>
          Email
          <input formControlName="email" type="email" autocomplete="username" maxlength="320" />
        </label>
        <label>
          Password
          <input
            formControlName="password"
            type="password"
            [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'"
            maxlength="100"
          />
          @if (mode() === 'register') {
            <small>At least 10 characters.</small>
          }
        </label>

        <button class="primary" type="submit" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Working…' : mode() === 'login' ? 'Sign in' : 'Create account' }}
        </button>
      </form>

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      <button class="link" type="button" (click)="toggleMode()">
        {{ mode() === 'login' ? 'Need an account? Register' : 'Already registered? Sign in' }}
      </button>
    </section>
  `,
  styles: [
    `
      .panel {
        max-width: 420px;
        margin: 60px auto;
        background: #0c1c17;
        border: 1px solid #214335;
        border-radius: 4px;
        padding: 32px;
      }
      h2 {
        margin: 0 0 8px;
        font-size: 19px;
      }
      .hint {
        margin: 0 0 24px;
        color: #7c9b8d;
        font-size: 13px;
        line-height: 1.5;
      }
      label {
        display: block;
        margin-bottom: 16px;
        font: 500 11px 'DM Mono', monospace;
        letter-spacing: 1px;
        text-transform: uppercase;
        color: #a8c5b4;
      }
      input {
        display: block;
        width: 100%;
        margin-top: 8px;
        padding: 11px 12px;
        background: #07120f;
        border: 1px solid #32624d;
        border-radius: 3px;
        color: #e7f1eb;
        font: 400 14px 'DM Mono', monospace;
      }
      input:focus {
        outline: 2px solid #67f0a2;
        outline-offset: 1px;
      }
      small {
        display: block;
        margin-top: 6px;
        text-transform: none;
        letter-spacing: 0;
        color: #6f8d7f;
      }
      .primary {
        width: 100%;
        padding: 12px;
        margin-top: 8px;
        border: none;
        border-radius: 3px;
        background: #67f0a2;
        color: #06110c;
        font-weight: 800;
        cursor: pointer;
      }
      .primary:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .link {
        display: block;
        width: 100%;
        margin-top: 18px;
        background: none;
        border: none;
        color: #7c9b8d;
        font-size: 12px;
        cursor: pointer;
        text-decoration: underline;
      }
      .error {
        margin-top: 16px;
        color: #ff9a9a;
        font: 500 12px 'DM Mono', monospace;
      }
    `,
  ],
})
export class AuthPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly mode = signal<'login' | 'register'>('register');
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    displayName: ['Demo Trader', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
  });

  protected toggleMode(): void {
    this.mode.update((current) => (current === 'login' ? 'register' : 'login'));
    this.error.set('');
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');

    const { email, password, displayName } = this.form.getRawValue();
    const request$ =
      this.mode() === 'login'
        ? this.auth.login(email, password)
        : this.auth.register(email, password, displayName);

    request$.subscribe({
      next: () => this.busy.set(false),
      error: (failure: { error?: { error?: string } }) => {
        this.busy.set(false);
        this.error.set(failure.error?.error ?? 'Unable to reach the trading platform.');
      },
    });
  }
}
