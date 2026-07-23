import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, map } from 'rxjs';
import { Router } from '@angular/router';

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
  private readonly CAMEL_BASE = 'http://localhost:9090';

  private readonly _isLoggedIn = signal(false);
  private readonly _username   = signal<string | null>(null);

  readonly isLoggedIn = this._isLoggedIn.asReadonly();
  readonly username   = this._username.asReadonly();

  constructor(private http: HttpClient, private router: Router) {
    this.restoreSession();
  }

  login(username: string, password: string): Observable<void> {
    return this.http
      .post<TokenResponse>(`${this.CAMEL_BASE}/fzlbpms/auth/login`, { username, password })
      .pipe(
        tap(tokens => this.persistTokens(tokens, username)),
        map(() => void 0),
      );
  }

  logout(): void {
    const refreshToken = localStorage.getItem(KEYS.refreshToken);
    if (refreshToken) {
      this.http
        .post(`${this.CAMEL_BASE}/fzlbpms/auth/logout`, { refresh_token: refreshToken })
        .subscribe({ error: () => {} });
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
