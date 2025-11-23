import { Component, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { invoke } from '@tauri-apps/api/core';

@Component({
  selector: 'app-fzlbpms-containers-stacks-view',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatCheckboxModule],
  templateUrl: './fzlbpms-containers-stacks-view.html',
  styleUrls: ['./fzlbpms-containers-stacks-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FzlbpmsContainersStacksView implements OnInit {
  services = signal<string[]>([]);
  form: FormGroup;

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({});
  }

  async ngOnInit() {
    try {
      const services = await invoke<string[]>("get_docker_compose_services");
      this.services.set(services);
      this.buildForm();
    } catch (error) {
      console.error("Error fetching docker-compose services:", error);
    }
  }

  buildForm() {
    const controls: { [key: string]: any } = {};
    this.services().forEach(service => {
      controls[service] = this.fb.control(false);
    });
    this.form = this.fb.group(controls);
  }

  async onSubmit() {
    const selectedServices = Object.keys(this.form.value).filter(
      key => this.form.value[key]
    );

    if (selectedServices.length === 0) {
      console.warn("No services selected.");
      return;
    }

    try {
      const result = await invoke<string>("run_docker_compose_up", { services: selectedServices });
      console.log("docker-compose-up.sh output:", result);
    } catch (error) {
      console.error("Error running docker-compose-up.sh:", error);
    }
  }
}
