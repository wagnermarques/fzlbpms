import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { invoke } from '@tauri-apps/api/core';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Container } from '../fzlbpms-containers-view/fzlbpms-containers-view';

@Component({
  selector: 'app-container-details-view',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatDividerModule,
    MatListModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './container-details-view.html',
  styleUrls: ['./container-details-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetailsView implements OnInit {
  container = signal<Container | null>(null);
  logs = signal<string[]>([]);
  error = signal<string | null>(null);

  private containerId: string | null = null;

  constructor(private route: ActivatedRoute) {}

  async ngOnInit() {
    this.containerId = this.route.snapshot.paramMap.get('id');
    if (!this.containerId) {
      this.error.set('No container id in the route.');
      return;
    }
    try {
      const containers = await invoke<Container[]>('list_running_containers');
      this.container.set(containers.find(c => c.id === this.containerId) ?? null);
    } catch (error) {
      this.error.set(`Error fetching container info: ${error}`);
    }
    await this.refreshLogs();
  }

  async refreshLogs() {
    if (!this.containerId) {
      return;
    }
    try {
      const logs = await invoke<string[]>('get_container_logs', { containerId: this.containerId });
      this.logs.set(logs);
    } catch (error) {
      this.error.set(`Error fetching logs: ${error}`);
    }
  }
}
