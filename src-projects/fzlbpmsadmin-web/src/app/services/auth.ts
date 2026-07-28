import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { deriveCodeChallenge, generateCodeVerifier, generateState } from './pkce';

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  id_token: string;
  expires_in: number;
  token_type: string;
}

export interface AuthUser {
  username: string;
  name: string;
}

const ACCESS_TOKEN_KEY = 'fzlbpms.access_token';
const REFRESH_TOKEN_KEY = 'fzlbpms.refresh_token';
const ID_TOKEN_KEY = 'fzlbpms.id_token';
const PKCE_VERIFIER_KEY = 'fzlbpms.pkce_verifier';
const OAUTH_STATE_KEY = 'fzlbpms.oauth_state';

const REDIRECT_URI = () => `${window.location.origin}/fzlbpmsadmin/auth-callback`;
const KEYCLOAK_ISSUER = () => `${window.location.origin}${environment.keycloakRealmPath}`;
const TOKEN_ENDPOINT = () => `${KEYCLOAK_ISSUER()}/protocol/openid-connect/token`;
const FORM_HEADERS = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

function decodeJwtPayload(token: string): Record<string, unknown> {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(base64));
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  currentUser = signal<AuthUser | null>(this.readStoredUser());

  /** Redirects the browser to Keycloak's hosted login page (Authorization Code + PKCE). */
  async login(): Promise<void> {
    const codeVerifier = generateCodeVerifier();
    const state = generateState();
    const codeChallenge = await deriveCodeChallenge(codeVerifier);

    sessionStorage.setItem(PKCE_VERIFIER_KEY, codeVerifier);
    sessionStorage.setItem(OAUTH_STATE_KEY, state);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: environment.keycloakClientId,
      redirect_uri: REDIRECT_URI(),
      scope: 'openid profile email',
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
    });

    window.location.href = `${KEYCLOAK_ISSUER()}/protocol/openid-connect/auth?${params}`;
  }

  /** Exchanges the authorization code for tokens. Call from the /auth-callback route. */
  handleCallback(code: string, state: string): Observable<TokenResponse> {
    const expectedState = sessionStorage.getItem(OAUTH_STATE_KEY);
    const codeVerifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(OAUTH_STATE_KEY);
    sessionStorage.removeItem(PKCE_VERIFIER_KEY);

    if (!codeVerifier || !expectedState || state !== expectedState) {
      throw new Error('OAuth callback state mismatch — possible CSRF or expired session.');
    }

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: environment.keycloakClientId,
      code,
      redirect_uri: REDIRECT_URI(),
      code_verifier: codeVerifier,
    });

    return this.http
      .post<TokenResponse>(TOKEN_ENDPOINT(), body.toString(), { headers: FORM_HEADERS })
      .pipe(tap((tokens) => this.storeSession(tokens)));
  }

  refresh(): Observable<TokenResponse> {
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: environment.keycloakClientId,
      refresh_token: localStorage.getItem(REFRESH_TOKEN_KEY) ?? '',
    });

    return this.http
      .post<TokenResponse>(TOKEN_ENDPOINT(), body.toString(), { headers: FORM_HEADERS })
      .pipe(tap((tokens) => this.storeSession(tokens)));
  }

  /** Full-page redirect to Keycloak's end-session endpoint so the SSO cookie is actually cleared. */
  logout(): void {
    const idToken = localStorage.getItem(ID_TOKEN_KEY);
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(ID_TOKEN_KEY);
    this.currentUser.set(null);

    const params = new URLSearchParams({
      client_id: environment.keycloakClientId,
      post_logout_redirect_uri: `${window.location.origin}/fzlbpmsadmin/`,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    window.location.href = `${KEYCLOAK_ISSUER()}/protocol/openid-connect/logout?${params}`;
  }

  private storeSession(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token);
    localStorage.setItem(ID_TOKEN_KEY, tokens.id_token);
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
        localStorage.removeItem(ID_TOKEN_KEY);
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
