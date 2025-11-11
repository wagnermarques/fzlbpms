import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { invoke } from '@tauri-apps/api/tauri';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <main class="main">
      <button (click)="listContainers()">List Containers</button>
    </main>
    <router-outlet />
  `,
  styles: `
    .main {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100vh;
    }
  `
})
export class App {
  protected readonly title = signal('angular-ui');

  async listContainers() {
    try {
      const containers = await invoke('list_running_containers');
      console.log(containers);
    } catch (e) {
      console.error(e);
    }
  }
}
