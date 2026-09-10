import {
  Component,
  ElementRef,
  OnDestroy,
  AfterViewInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import * as L from 'leaflet';
import { ContatemeantesService, DeviceLocation } from '../../../services/contatemeantes';

// Leaflet resolves its default marker icon images via <script src>
// introspection, which doesn't exist under a bundler — without this the
// default markers are broken image icons. Pointed at unpkg, pinned to the
// installed leaflet version.
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const POLL_INTERVAL_MS = 10_000;
const DEFAULT_CENTER: L.LatLngTuple = [-23.5505, -46.6333]; // São Paulo

@Component({
  selector: 'app-contatemeantes-map-view',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatSelectModule, MatIconModule],
  templateUrl: './contatemeantes-map-view.html',
  styleUrl: './contatemeantes-map-view.css',
})
export class ContatemeantesMapView implements AfterViewInit, OnDestroy {
  private contatemeantes = inject(ContatemeantesService);

  mapContainer = viewChild.required<ElementRef<HTMLDivElement>>('mapContainer');

  groups = signal<string[]>([]);
  selectedGroup = signal<string | null>(null);
  locations = signal<DeviceLocation[]>([]);
  errorMessage = signal<string | null>(null);

  private map: L.Map | null = null;
  private markers = new Map<string, L.Marker>();
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngAfterViewInit(): void {
    this.map = L.map(this.mapContainer().nativeElement).setView(DEFAULT_CENTER, 12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);

    this.contatemeantes.listGroups().subscribe({
      next: (groups) => this.groups.set(groups),
      error: () => this.errorMessage.set('Could not load the list of groups.'),
    });
  }

  onGroupSelected(groupId: string): void {
    this.selectedGroup.set(groupId);
    this.errorMessage.set(null);
    this.stopPolling();
    this.refresh(groupId);
    this.pollHandle = setInterval(() => this.refresh(groupId), POLL_INTERVAL_MS);
  }

  private refresh(groupId: string): void {
    this.contatemeantes.getGroupLocations(groupId).subscribe({
      next: (locations) => {
        this.locations.set(locations);
        this.errorMessage.set(null);
        this.renderMarkers(locations);
      },
      error: () => this.errorMessage.set(`Could not load locations for group "${groupId}".`),
    });
  }

  private renderMarkers(locations: DeviceLocation[]): void {
    if (!this.map) {
      return;
    }

    const seen = new Set<string>();
    for (const location of locations) {
      seen.add(location.deviceId);
      const latLng: L.LatLngTuple = [location.latitude, location.longitude];
      const popup = this.popupHtml(location);

      const existing = this.markers.get(location.deviceId);
      if (existing) {
        existing.setLatLng(latLng).setPopupContent(popup);
      } else {
        const marker = L.marker(latLng).addTo(this.map).bindPopup(popup);
        this.markers.set(location.deviceId, marker);
      }
    }

    // Drop markers for devices that left the group or dropped out of the response.
    for (const [deviceId, marker] of this.markers) {
      if (!seen.has(deviceId)) {
        marker.remove();
        this.markers.delete(deviceId);
      }
    }

    if (locations.length > 0) {
      const bounds = L.latLngBounds(locations.map((l) => [l.latitude, l.longitude] as L.LatLngTuple));
      this.map.fitBounds(bounds, { padding: [40, 40], maxZoom: 16 });
    }
  }

  private popupHtml(location: DeviceLocation): string {
    const name = location.userName ?? location.deviceId;
    const battery = location.batteryLevel != null ? `${location.batteryLevel}%` : 'n/a';
    const charging = location.isCharging ? ' (charging)' : '';
    const lastSeen = new Date(location.lastSeen).toLocaleString('pt-BR');
    return `<strong>${name}</strong><br>Battery: ${battery}${charging}<br>Last seen: ${lastSeen}`;
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.map?.remove();
  }
}
