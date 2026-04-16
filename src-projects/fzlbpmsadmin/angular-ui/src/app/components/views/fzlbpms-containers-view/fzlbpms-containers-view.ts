import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { invoke } from '@tauri-apps/api/core';

export interface Container {
  id: string;
  name: string;
  image: string;
  state: string;
  status: string;
}

@Component({
  selector: 'app-fzlbpms-containers-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './fzlbpms-containers-view.html',
  styleUrls: ['./fzlbpms-containers-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FzlbpmsContainersView implements OnInit {
  containers = signal<Container[]>([]);

  async ngOnInit() {
    try {
      const containers = await invoke<Container[]>("list_running_containers");
      this.containers.set(containers);
    } catch (error) {
      console.error("Error fetching containers:", error);
    }
  }

  async getContainerLogs(id: string) {
    try {
      const logs = await invoke<string[]>("get_container_logs", { containerId: id });
      console.log(`Logs for container ${id}:`, logs);
    } catch (error) {
      console.error(`Error fetching logs for container ${id}:`, error);
    }
  }
}
