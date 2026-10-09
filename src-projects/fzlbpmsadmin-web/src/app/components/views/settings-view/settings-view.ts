import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SettingsApi, FzlbpmsSettings } from '../../../services/settings-api';

@Component({
  selector: 'app-settings-view',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatSlideToggleModule, MatButtonModule, MatIconModule],
  templateUrl: './settings-view.html',
  styleUrl: './settings-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsView implements OnInit {
  private api = inject(SettingsApi);

  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);
  saved = signal(false);

  // Working copy — only written back to the server on "Salvar", so a
  // toggle flip doesn't take effect until confirmed (this one gates
  // internet access to the Keycloak admin console).
  keycloakAdminPublic = signal(true);

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (settings) => {
        this.keycloakAdminPublic.set(settings.keycloakAdminPublic);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Falha ao carregar as configurações.');
        this.loading.set(false);
      },
    });
  }

  toggle(): void {
    this.keycloakAdminPublic.set(!this.keycloakAdminPublic());
    this.saved.set(false);
  }

  save(): void {
    this.saving.set(true);
    this.saved.set(false);
    const settings: FzlbpmsSettings = { keycloakAdminPublic: this.keycloakAdminPublic() };
    this.api.put(settings).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: () => {
        this.error.set('Falha ao salvar as configurações.');
        this.saving.set(false);
      },
    });
  }
}
