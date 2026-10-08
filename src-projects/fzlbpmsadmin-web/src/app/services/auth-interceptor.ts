import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth';
import { environment } from '../../environments/environment';

/** Attaches the Keycloak access token to calls against the admin API — see
 * kc-authorize-admin in keycloak-admin-camel-context.xml, which requires it. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(`${environment.apiUrl}/admin/`)) {
    return next(req);
  }

  const token = inject(AuthService).getAccessToken();
  if (!token) return next(req);

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
