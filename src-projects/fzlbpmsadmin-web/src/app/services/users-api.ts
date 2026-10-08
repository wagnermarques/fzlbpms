import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface KeycloakUser {
  id: string;
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
  emailVerified?: boolean;
}

export interface KeycloakRole {
  id: string;
  name: string;
  description?: string;
}

export interface NewUser {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
  credentials: [{ type: 'password'; value: string; temporary: boolean }];
}

const BASE = `${environment.apiUrl}/admin`;

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private http = inject(HttpClient);

  listUsers(): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(`${BASE}/users`);
  }

  createUser(user: NewUser): Observable<void> {
    return this.http.post<void>(`${BASE}/users`, user);
  }

  updateUser(id: string, patch: Partial<KeycloakUser>): Observable<void> {
    return this.http.put<void>(`${BASE}/users/${id}`, patch);
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/users/${id}`);
  }

  resetPassword(id: string, password: string, temporary: boolean): Observable<void> {
    return this.http.put<void>(`${BASE}/users/${id}/password`, { password, temporary });
  }

  listRoles(): Observable<KeycloakRole[]> {
    return this.http.get<KeycloakRole[]>(`${BASE}/roles`);
  }

  getUserRoles(id: string): Observable<KeycloakRole[]> {
    return this.http.get<KeycloakRole[]>(`${BASE}/users/${id}/roles`);
  }

  grantRole(id: string, roleName: string): Observable<void> {
    return this.http.post<void>(`${BASE}/users/${id}/roles`, { name: roleName });
  }

  revokeRole(id: string, roleName: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/users/${id}/roles/${roleName}`);
  }
}
