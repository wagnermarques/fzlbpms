import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Router } from '@angular/router';

export interface Application {
  name: string;
  url: string;
  description?: string;
  credentials?: {
    user: string;
    pass: string;
  };
  isProject?: boolean;
}

@Component({
  selector: 'app-apps-home-view',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatTabsModule, MatCardModule, MatIconModule],
  templateUrl: './apps-home-view.html',
  styleUrls: ['./apps-home-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppsHomeView implements OnInit {
  applications = signal<Application[]>([]);
  installationProgress = signal<string[]>([]);

  constructor(private router: Router) {}

  async ngOnInit() {
    this.loadApplications();

    await listen<string>('moodle-installation-progress', (event) => {
      this.installationProgress.update(progress => [...progress, event.payload]);
    });
  }

  async loadApplications() {
    const mainApps: Application[] = [
      {
        name: 'Flowable BPM',
        url: 'http://localhost:8080/flowable-ui',
        description: 'BPMN 2.0 Process engine — Modeler, Task, Admin and IDM apps bundled in a single UI (v6.7.2).',
        credentials: { user: 'admin', pass: 'test' }
      },
      {
        name: 'BPMN Drawer',
        url: 'http://localhost:8085/',
        description: 'Web-based BPMN 2.0 modeler (bpmn-js) for designing and editing process diagrams.'
      },
      {
        name: 'Moodle',
        url: 'http://localhost/moodle',
        description: 'Learning Management System (LMS) for educational workflows.',
        credentials: { user: 'admin', pass: 'admin' }
      },
      {
        name: 'Keycloak',
        url: 'http://localhost/auth/',
        description: 'Identity and Access Management (IAM) for centralized authentication.',
        credentials: { user: 'admin', pass: 'admin' }
      },
      {
        name: 'Karaf Console',
        url: 'http://localhost/karafconsole/',
        description: 'Web console for Apache Karaf (OSGi container), managing bundles and services.',
        credentials: { user: 'karaf', pass: 'karaf' }
      }
    ];

    try {
      const dynamicProjects = await invoke<any[]>("list_projects");
      const projectApps: Application[] = dynamicProjects.map(p => {
        let description = `Project directory served at ${p.url}`;
        let credentials = undefined;
        
        if (p.name === 'moodle') {
          description = 'Moodle LMS Installation';
          credentials = { user: 'admin', pass: 'admin' };
        } else if (p.name.includes('karaf')) {
          description = 'Karaf-related directory for deployment or integration.';
        } else if (p.name === 'fzlbpmsadmin') {
          description = 'Administration GUI for the BPMS environment.';
        }

        return {
          name: p.name,
          url: p.url,
          description,
          credentials,
          isProject: true
        };
      });
      
      // Combine and remove duplicates based on URL
      const combined = [...mainApps];
      projectApps.forEach(pa => {
        if (!combined.find(c => c.url === pa.url)) {
          combined.push(pa);
        }
      });

      this.applications.set(combined);
    } catch (error) {
      console.error("Error fetching projects:", error);
      this.applications.set(mainApps);
    }
  }

  async openUrl(url: string) {
    try {
      await invoke("plugin:shell|open", { path: url });
    } catch (error) {
      console.error(`Error opening url ${url}:`, error);
    }
  }

  async installMoodle() {
    this.router.navigate(['/moodle-install']);
  }

  installJoomla() {
    console.log("Install Joomla button clicked");
  }

  createAngularApp() {
    console.log("Create Angular App button clicked");
  }
}
