import { Component, ChangeDetectionStrategy, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TasksApi, PendingTask } from '../../../services/tasks-api';
import { AuthService } from '../../../services/auth';

@Component({
  selector: 'app-tasks-view',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './tasks-view.html',
  styleUrl: './tasks-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksView implements OnInit {
  private api = inject(TasksApi);
  private auth = inject(AuthService);

  tasks = signal<PendingTask[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  signedOut = signal(false);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    // Without a token the API can only answer 403 — say so in plain language
    // instead of surfacing the raw error.
    if (!this.auth.getAccessToken()) {
      this.signedOut.set(true);
      this.tasks.set([]);
      return;
    }
    this.signedOut.set(false);
    this.loading.set(true);
    this.error.set(null);
    this.api.listMyTasks().subscribe({
      next: (tasks) => {
        this.tasks.set(tasks);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.message || 'Falha ao carregar as tarefas.');
      },
    });
  }

  /** Full-page navigation on purpose: the form sits behind the oauth2-proxy
   * gate, which needs a real browser redirect to establish its session (the
   * Keycloak sign-in itself is already done, so it completes without a
   * prompt). An XHR here would just fetch the sign-in redirect. */
  open(task: PendingTask): void {
    window.location.href = task.openUrl;
  }
}
