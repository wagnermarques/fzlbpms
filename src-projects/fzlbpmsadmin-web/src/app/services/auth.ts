import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
}

export interface AuthUser {
  username: string;
  name: string;
}

const ACCESS_TOKEN_KEY = 'fzlbpms.access_token';
const REFRESH_TOKEN_KEY = 'fzlbpms.refresh_token';

function decodeJwtPayload(token: string): Record<string, unknown> {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(base64));
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  currentUser = signal<AuthUser | null>(this.readStoredUser());

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/login`, { username, password })
      .pipe(tap((tokens) => this.storeSession(tokens)));
  }

  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.currentUser.set(null);

    if (refreshToken) {
      this.http
        .post(`${environment.apiUrl}/auth/logout`, { refresh_token: refreshToken })
        .subscribe({ error: () => {} });
    }
  }

  private storeSession(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token);
    this.currentUser.set(this.userFromToken(tokens.access_token));
  }

  private readStoredUser(): AuthUser | null {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) return null;

    try {
      const claims = decodeJwtPayload(token) as { exp: number };
      if (claims.exp * 1000 < Date.now()) {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        return null;
      }
      return this.userFromToken(token);
    } catch {
      return null;
    }
  }

  private userFromToken(token: string): AuthUser {
    const claims = decodeJwtPayload(token) as { preferred_username?: string; name?: string };
    const username = claims.preferred_username ?? 'unknown';
    return { username, name: claims.name ?? username };
  }
}
