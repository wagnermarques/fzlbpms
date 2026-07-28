import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-login-view',
  templateUrl: './login-view.html',
  styleUrl: './login-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
})
export class LoginView implements OnInit {
  username = '';
  password = '';

  readonly loading      = signal(false);
  readonly error        = signal<string | null>(null);
  readonly showPassword = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/']);
    }
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  onLogin(): void {
    if (!this.username || !this.password) return;

    this.loading.set(true);
    this.error.set(null);

    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/']);
      },
      error: err => {
        this.loading.set(false);
        if (err.status === 401) {
          this.error.set('Usuário ou senha inválidos.');
        } else if (err.status === 0) {
          this.error.set('Não foi possível conectar ao servidor. Verifique se o serviço Camel está em execução (porta 9090).');
        } else {
          this.error.set(`Erro ao realizar login (${err.status}). Tente novamente.`);
        }
      },
    });
  }
}
