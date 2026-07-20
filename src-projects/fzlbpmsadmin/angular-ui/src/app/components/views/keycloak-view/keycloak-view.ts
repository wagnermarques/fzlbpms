import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { openExternal } from '../fzlbpms-containers-view/service-access';

@Component({
  selector: 'app-keycloak-view',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatDividerModule,
    MatListModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './keycloak-view.html',
  styleUrls: ['./keycloak-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KeycloakView {
  keycloakInfo = {
    defaultUser: 'admin',
    defaultPassword: 'admin',
    gatewayUrl: 'http://localhost/auth/admin',
    directUrl: 'http://localhost:8083/auth/admin',
    relativeContext: '/auth',
    database: 'fzl-keycloak-db (PostgreSQL 16)',
    version: '26.0.0'
  };

  openLink(url: string) {
    openExternal(url);
  }
}
