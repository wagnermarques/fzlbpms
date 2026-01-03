import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Router } from '@angular/router';

export interface Project {
  name: string;
  url: string;
}

@Component({
  selector: 'app-apps-home-view',
  standalone: true,
  imports: [CommonModule, MatButtonModule],
  templateUrl: './apps-home-view.html',
  styleUrls: ['./apps-home-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppsHomeView implements OnInit {
  projects = signal<Project[]>([]);
  installationProgress = signal<string[]>([]);

  constructor(private router: Router) {}

  async ngOnInit() {
    try {
      const projects = await invoke<Project[]>("list_projects");
      this.projects.set(projects);
    } catch (error) {
      console.error("Error fetching projects:", error);
    }

    await listen<string>('moodle-installation-progress', (event) => {
      this.installationProgress.update(progress => [...progress, event.payload]);
    });
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
