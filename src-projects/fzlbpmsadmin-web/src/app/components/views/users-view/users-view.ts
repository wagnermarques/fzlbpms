import { Component, ChangeDetectionStrategy, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { UsersApi, KeycloakUser, KeycloakRole, NewUser } from '../../../services/users-api';

// Keycloak's own built-in roles — never meaningful to toggle from this UI.
const SYSTEM_ROLES = new Set(['offline_access', 'uma_authorization']);
const isSystemRole = (name: string) => SYSTEM_ROLES.has(name) || name.startsWith('default-roles-');

interface RoleGroup {
  label: string;
  roles: KeycloakRole[];
}

@Component({
  selector: 'app-users-view',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatCardModule,
    MatCheckboxModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './users-view.html',
  styleUrl: './users-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersView implements OnInit {
  private api = inject(UsersApi);

  users = signal<KeycloakUser[]>([]);
  allRoles = signal<KeycloakRole[]>([]);
  columns = ['username', 'email', 'enabled', 'actions'];
  loading = signal(false);
  error = signal<string | null>(null);

  roleGroups = computed<RoleGroup[]>(() => {
    const groups = new Map<string, KeycloakRole[]>();
    for (const role of this.allRoles()) {
      if (isSystemRole(role.name)) continue;
      const label = role.name.includes('-') ? role.name.split('-')[0] : 'global';
      groups.set(label, [...(groups.get(label) ?? []), role]);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([label, roles]) => ({ label, roles }));
  });

  // -- create user form --
  showCreateForm = signal(false);
  newUsername = '';
  newEmail = '';
  newFirstName = '';
  newLastName = '';
  newPassword = '';
  newTemporary = true;

  // -- role editor panel --
  roleEditorUserId = signal<string | null>(null);
  roleEditorUser = computed(() => this.users().find((u) => u.id === this.roleEditorUserId()));
  roleEditorSelected = signal<Set<string>>(new Set());
  roleEditorBusy = signal(false);

  // -- reset password panel --
  passwordEditorUserId = signal<string | null>(null);
  passwordEditorUser = computed(() => this.users().find((u) => u.id === this.passwordEditorUserId()));
  resetPassword = '';
  resetTemporary = true;

  ngOnInit(): void {
    this.reload();
    this.api.listRoles().subscribe({
      next: (roles) => this.allRoles.set(roles),
      error: () => this.error.set('Falha ao carregar a lista de roles.'),
    });
  }

  reload(): void {
    this.loading.set(true);
    this.api.listUsers().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Falha ao carregar usuários.');
        this.loading.set(false);
      },
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.set(!this.showCreateForm());
  }

  submitCreateUser(): void {
    if (!this.newUsername || !this.newPassword) return;
    const user: NewUser = {
      username: this.newUsername,
      email: this.newEmail || undefined,
      firstName: this.newFirstName || undefined,
      lastName: this.newLastName || undefined,
      enabled: true,
      credentials: [{ type: 'password', value: this.newPassword, temporary: this.newTemporary }],
    };
    this.api.createUser(user).subscribe({
      next: () => {
        this.newUsername = this.newEmail = this.newFirstName = this.newLastName = this.newPassword = '';
        this.newTemporary = true;
        this.showCreateForm.set(false);
        this.reload();
      },
      error: () => this.error.set('Falha ao criar usuário.'),
    });
  }

  toggleEnabled(user: KeycloakUser): void {
    this.api.updateUser(user.id, { enabled: !user.enabled }).subscribe({
      next: () => this.reload(),
      error: () => this.error.set(`Falha ao atualizar ${user.username}.`),
    });
  }

  deleteUser(user: KeycloakUser): void {
    if (!confirm(`Excluir o usuário "${user.username}"? Esta ação não pode ser desfeita.`)) return;
    this.api.deleteUser(user.id).subscribe({
      next: () => this.reload(),
      error: () => this.error.set(`Falha ao excluir ${user.username}.`),
    });
  }

  openRoleEditor(user: KeycloakUser): void {
    this.passwordEditorUserId.set(null);
    this.roleEditorUserId.set(user.id);
    this.api.getUserRoles(user.id).subscribe({
      next: (roles) => this.roleEditorSelected.set(new Set(roles.map((r) => r.name))),
      error: () => this.error.set(`Falha ao carregar roles de ${user.username}.`),
    });
  }

  closeRoleEditor(): void {
    this.roleEditorUserId.set(null);
  }

  toggleRoleSelection(roleName: string): void {
    const current = new Set(this.roleEditorSelected());
    if (current.has(roleName)) current.delete(roleName);
    else current.add(roleName);
    this.roleEditorSelected.set(current);
  }

  saveRoleEditor(user: KeycloakUser): void {
    this.roleEditorBusy.set(true);
    this.api.getUserRoles(user.id).subscribe({
      next: (currentRoles) => {
        const before = new Set(currentRoles.map((r) => r.name));
        const after = this.roleEditorSelected();
        const toGrant = [...after].filter((r) => !before.has(r));
        const toRevoke = [...before].filter((r) => !isSystemRole(r) && !after.has(r));

        const calls = [
          ...toGrant.map((r) => this.api.grantRole(user.id, r)),
          ...toRevoke.map((r) => this.api.revokeRole(user.id, r)),
        ];
        if (calls.length === 0) {
          this.roleEditorBusy.set(false);
          this.closeRoleEditor();
          return;
        }
        let remaining = calls.length;
        const done = () => {
          if (--remaining === 0) {
            this.roleEditorBusy.set(false);
            this.closeRoleEditor();
          }
        };
        calls.forEach((call) =>
          call.subscribe({
            next: done,
            error: () => {
              this.error.set(`Falha ao atualizar roles de ${user.username}.`);
              done();
            },
          }),
        );
      },
      error: () => this.roleEditorBusy.set(false),
    });
  }

  openPasswordEditor(user: KeycloakUser): void {
    this.roleEditorUserId.set(null);
    this.passwordEditorUserId.set(user.id);
    this.resetPassword = '';
    this.resetTemporary = true;
  }

  closePasswordEditor(): void {
    this.passwordEditorUserId.set(null);
  }

  submitResetPassword(user: KeycloakUser): void {
    if (!this.resetPassword) return;
    this.api.resetPassword(user.id, this.resetPassword, this.resetTemporary).subscribe({
      next: () => this.closePasswordEditor(),
      error: () => this.error.set(`Falha ao redefinir a senha de ${user.username}.`),
    });
  }
}
