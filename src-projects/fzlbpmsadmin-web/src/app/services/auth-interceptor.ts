import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth';
import { environment } from '../../environments/environment';

/** Attaches the Keycloak access token to calls against the admin API — see
 * kc-authorize-admin in keycloak-admin-camel-context.xml, which requires it —
 * and against the task inbox, which uses kc-authorize-user (any authenticated
 * user, no role required) in the same context. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const needsToken =
    req.url.startsWith(`${environment.apiUrl}/admin/`) ||
    req.url.startsWith(`${environment.apiUrl}/tasks`);
  if (!needsToken) {
    return next(req);
  }

  const token = inject(AuthService).getAccessToken();
  if (!token) return next(req);

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
