import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth';

@Component({
  selector: 'app-auth-callback-view',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './auth-callback-view.html',
  styleUrl: './auth-callback-view.css',
})
export class AuthCallbackView implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);

  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const error = params.get('error');
    const code = params.get('code');
    const state = params.get('state');

    if (error) {
      this.errorMessage.set(params.get('error_description') ?? error);
      return;
    }
    if (!code || !state) {
      this.errorMessage.set('Missing authorization code in callback.');
      return;
    }

    try {
      this.auth.handleCallback(code, state).subscribe({
        next: () => this.router.navigateByUrl('/'),
        error: () => this.errorMessage.set('Could not complete login. Please try again.'),
      });
    } catch (e) {
      this.errorMessage.set(e instanceof Error ? e.message : 'Login failed.');
    }
  }
}
