import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { invoke } from '@tauri-apps/api/core';
import { getServiceAccess, openExternal, ServiceAccess } from './service-access';

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

  constructor(private router: Router) {}

  async ngOnInit() {
    try {
      const containers = await invoke<Container[]>("list_running_containers");
      this.containers.set(containers);
    } catch (error) {
      console.error("Error fetching containers:", error);
    }
  }

  async onContainerClick(container: Container) {
    if (container.name.includes('fzl-keycloak')) {
      this.router.navigate(['/keycloak-view']);
    } else {
      this.router.navigate(['/container-details', container.id]);
    }
  }

  accessFor(container: Container): ServiceAccess | undefined {
    return getServiceAccess(container.name);
  }

  openLink(url: string) {
    openExternal(url);
  }
}
