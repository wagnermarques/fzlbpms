import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DeviceLocation {
  deviceId: string;
  userName: string | null;
  latitude: number;
  longitude: number;
  accuracy: number | null;
  batteryLevel: number | null;
  isCharging: boolean | null;
  lastSeen: string;
}

@Injectable({ providedIn: 'root' })
export class ContatemeantesService {
  private http = inject(HttpClient);

  // Same-origin, ungated (see containers/fzl-nginx/nginx-shared/app-server.conf
  // "location /contatemeantes/") — no auth yet, matching the rest of this
  // bundle while it's still being tested against the Android app.
  listGroups(): Observable<string[]> {
    return this.http.get<string[]>('/contatemeantes/groups');
  }

  getGroupLocations(groupId: string): Observable<DeviceLocation[]> {
    return this.http.get<DeviceLocation[]>(`/contatemeantes/group/${groupId}/locations`);
  }
}
