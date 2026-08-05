import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

export interface Application {
  name: string;
  url: string;
  description?: string;
  credentials?: {
    user: string;
    pass: string;
  };
}

@Component({
  selector: 'app-apps-home-view',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatTabsModule, MatCardModule, MatIconModule],
  templateUrl: './apps-home-view.html',
  styleUrls: ['./apps-home-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppsHomeView {
  // Same-origin apps are reverse-proxied by fzl-nginx, so relative paths
  // work from whatever host/IP the admin app is accessed from. BPMN Drawer
  // is proxied at /bpmndrawer/ (containers/fzl-nginx/nginx-shared/app-server.conf)
  // rather than addressed by its container port directly, so it always
  // inherits the page's own scheme/host/TLS instead of needing its own cert.
  applications = signal<Application[]>([
    {
      name: 'Flowable BPM',
      url: '/flowable-ui/',
      description: 'BPMN 2.0 Process engine — Modeler, Task, Admin and IDM apps bundled in a single UI (v6.7.2).',
      credentials: { user: 'admin', pass: 'test' },
    },
    {
      name: 'BPMN Drawer',
      url: '/bpmndrawer/',
      description: 'Web-based BPMN 2.0 modeler (bpmn-js) for designing and editing process diagrams.',
    },
    {
      name: 'Orbeon Forms',
      url: '/orbeon/',
      description: 'Open-source forms platform (Community Edition) — visual Form Builder and Form Runner for process start forms and data collection.',
    },
    {
      name: 'Moodle',
      url: '/moodle',
      description: 'Learning Management System (LMS) for educational workflows.',
      credentials: { user: 'admin', pass: 'admin' },
    },
    {
      name: 'Keycloak',
      url: '/auth/',
      description: 'Identity and Access Management (IAM) for centralized authentication.',
      credentials: { user: 'admin', pass: 'admin' },
    },
    {
      name: 'Karaf Console',
      url: '/karafconsole/',
      description: 'Web console for Apache Karaf (OSGi container), managing bundles and services.',
      credentials: { user: 'karaf', pass: 'karaf' },
    },
  ]);
}
