import { Injectable, signal } from '@angular/core';
import { Observable, from } from 'rxjs';
import { Router } from '@angular/router';
import { invoke } from '@tauri-apps/api/core';

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
}

const KEYS = {
  accessToken:  'fzl_access_token',
  refreshToken: 'fzl_refresh_token',
  username:     'fzl_username',
  expires:      'fzl_token_expires',
} as const;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _isLoggedIn = signal(false);
  private readonly _username   = signal<string | null>(null);

  readonly isLoggedIn = this._isLoggedIn.asReadonly();
  readonly username   = this._username.asReadonly();

  constructor(private router: Router) {
    this.restoreSession();
  }

  login(username: string, password: string): Observable<void> {
    const p = invoke<TokenResponse>('camel_login', { username, password })
      .then(tokens => {
        this.persistTokens(tokens, username);
      })
      .catch(err => {
        // Rust returns the HTTP status code as a string ("401", "500")
        // or "0" for a connection-level failure.
        const status = parseInt(String(err), 10);
        const e: any = new Error(String(err));
        e.status = isNaN(status) ? 0 : status;
        throw e;
      });

    return from(p);
  }

  logout(): void {
    const refreshToken = localStorage.getItem(KEYS.refreshToken);
    if (refreshToken) {
      invoke('camel_logout', { refreshToken }).catch(() => {});
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(KEYS.accessToken);
  }

  private isTokenValid(): boolean {
    const expires = localStorage.getItem(KEYS.expires);
    return !!expires && Date.now() < Number(expires);
  }

  private persistTokens(tokens: TokenResponse, username: string): void {
    localStorage.setItem(KEYS.accessToken,  tokens.access_token);
    localStorage.setItem(KEYS.refreshToken, tokens.refresh_token);
    localStorage.setItem(KEYS.username,     username);
    localStorage.setItem(KEYS.expires,      String(Date.now() + tokens.expires_in * 1000));
    this._isLoggedIn.set(true);
    this._username.set(username);
  }

  private restoreSession(): void {
    const token = localStorage.getItem(KEYS.accessToken);
    if (token && this.isTokenValid()) {
      this._isLoggedIn.set(true);
      this._username.set(localStorage.getItem(KEYS.username));
    }
  }

  private clearSession(): void {
    Object.values(KEYS).forEach(k => localStorage.removeItem(k));
    this._isLoggedIn.set(false);
    this._username.set(null);
  }
}
