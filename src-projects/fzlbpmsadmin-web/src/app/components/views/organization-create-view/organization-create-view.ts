import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { OrganizationsApi } from '../../../services/organizations-api';

@Component({
  selector: 'app-organization-create-view',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './organization-create-view.html',
  styleUrl: './organization-create-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationCreateView {
  private api = inject(OrganizationsApi);

  name = '';
  alias = '';

  submitting = signal(false);
  error = signal<string | null>(null);
  created = signal<string | null>(null);

  create(): void {
    if (!this.name.trim() || !this.alias.trim()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.created.set(null);
    const name = this.name.trim();
    const alias = this.alias.trim();
    this.api.provisionOrganization(name, alias).subscribe({
      next: () => {
        this.submitting.set(false);
        this.created.set(name);
        this.name = '';
        this.alias = '';
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = err?.error?.error || err?.message || 'Falha ao criar a organização.';
        this.error.set(msg);
      },
    });
  }
}
