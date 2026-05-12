import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { invoke } from '@tauri-apps/api/core';

export interface DockerComposeService {
  name: string;
  container_name: string;
  container_id?: string;
  state?: string;
  status?: string;
}

@Component({
  selector: 'app-fzlbpms-containers-stacks-view',
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule, 
    MatCheckboxModule, 
    MatButtonModule, 
    MatIconModule,
    MatTableModule,
    MatChipsModule
  ],
  templateUrl: './fzlbpms-containers-stacks-view.html',
  styleUrls: ['./fzlbpms-containers-stacks-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FzlbpmsContainersStacksView implements OnInit {
  services = signal<DockerComposeService[]>([]);
  displayedColumns: string[] = ['select', 'name', 'container_name', 'status', 'actions'];
  form: FormGroup;
  isLoading = signal<boolean>(false);

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({});
  }

  async ngOnInit() {
    await this.refreshServices();
  }

  async refreshServices() {
    this.isLoading.set(true);
    try {
      const services = await invoke<DockerComposeService[]>("get_docker_compose_services_with_status");
      this.services.set(services);
      this.buildForm();
    } catch (error) {
      console.error("Error fetching docker-compose services:", error);
    } finally {
      this.isLoading.set(false);
    }
  }

  buildForm() {
    const controls: { [key: string]: any } = {};
    this.services().forEach(service => {
      controls[service.name] = this.fb.control(false);
    });
    this.form = this.fb.group(controls);
  }

  async onStartSelected() {
    const selectedServices = Object.keys(this.form.value).filter(
      key => this.form.value[key]
    );

    if (selectedServices.length === 0) {
      console.warn("No services selected.");
      return;
    }

    await this.runCommand("run_docker_compose_up", { services: selectedServices });
  }

  async onStopSelected() {
    const selectedServices = Object.keys(this.form.value).filter(
      key => this.form.value[key]
    );

    if (selectedServices.length === 0) {
      console.warn("No services selected.");
      return;
    }

    await this.runCommand("run_docker_compose_stop", { services: selectedServices });
  }

  async onStartService(serviceName: string) {
    await this.runCommand("run_docker_compose_up", { services: [serviceName] });
  }

  async onStopService(serviceName: string) {
    await this.runCommand("run_docker_compose_stop", { services: [serviceName] });
  }

  private async runCommand(command: string, args: any) {
    this.isLoading.set(true);
    try {
      const result = await invoke<string>(command, args);
      console.log(`${command} output:`, result);
      await this.refreshServices();
    } catch (error) {
      console.error(`Error running ${command}:`, error);
    } finally {
      this.isLoading.set(false);
    }
  }

  getStatusColor(state?: string): string {
    switch (state?.toLowerCase()) {
      case 'running': return 'primary';
      case 'exited': return 'warn';
      case 'restarting': return 'accent';
      default: return '';
    }
  }
}
