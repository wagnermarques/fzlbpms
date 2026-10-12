import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface KeycloakOrganization {
  id: string;
  name: string;
  alias: string;
  enabled?: boolean;
}

const BASE = `${environment.apiUrl}/admin/organizations`;

@Injectable({ providedIn: 'root' })
export class OrganizationsApi {
  private http = inject(HttpClient);

  listOrganizations(): Observable<KeycloakOrganization[]> {
    return this.http.get<KeycloakOrganization[]>(BASE);
  }

  /** Starts the create-organization Flowable process (see
   * kc-provision-organization in keycloak-admin-camel-context.xml), which
   * creates the organization plus its two default roles (org-<alias>-dono,
   * org-<alias>-membro). */
  provisionOrganization(name: string, alias: string): Observable<void> {
    return this.http.post<void>(`${BASE}/provision`, { name, alias });
  }
}
