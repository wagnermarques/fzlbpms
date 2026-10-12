import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type LogBundle = 'chamadasonline' | 'tasktoday' | 'evasaoescolar';

export type LogLevels = Record<LogBundle, string>;

const BASE = `${environment.apiUrl}/admin/logs`;

@Injectable({ providedIn: 'root' })
export class LogsApi {
  private http = inject(HttpClient);

  /** Current level (DEBUG/INFO) of each bundle's own logger category —
   * backed live by Karaf's log MBean, not a stored setting. */
  getLevels(): Observable<LogLevels> {
    return this.http.get<LogLevels>(`${BASE}/levels`);
  }

  setDebug(bundle: LogBundle, debug: boolean): Observable<{ bundle: string; level: string }> {
    return this.http.put<{ bundle: string; level: string }>(`${BASE}/levels/${bundle}`, { debug });
  }
}
