import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface FzlbpmsSettings {
  keycloakAdminPublic: boolean;
}

const BASE = `${environment.apiUrl}/admin/settings`;

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private http = inject(HttpClient);

  get(): Observable<FzlbpmsSettings> {
    return this.http.get<FzlbpmsSettings>(BASE);
  }

  put(settings: FzlbpmsSettings): Observable<void> {
    return this.http.put<void>(BASE, settings);
  }
}
