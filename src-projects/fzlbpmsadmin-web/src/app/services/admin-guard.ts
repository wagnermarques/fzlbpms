import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

/** UX-only gate — the real enforcement is kc-authorize-admin on the backend. */
export const adminGuard: CanActivateFn = () => {
  if (inject(AuthService).hasRole('admin')) return true;
  return inject(Router).createUrlTree(['/']);
};
