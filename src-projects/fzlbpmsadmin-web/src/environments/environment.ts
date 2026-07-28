export const environment = {
  apiUrl: '/fzlbpms',
  // Keycloak is reverse-proxied at /auth/ on the same origin the app itself
  // was loaded from (see auth.ts), so no hostname needs to be hardcoded here
  // — the app works unmodified whether it's loaded from localhost or the
  // public domain (FZL_PUBLIC_HOSTNAME). Only Keycloak's own KC_HOSTNAME
  // needs to match — see docker-compose.yml / bin/switch-domain.sh.
  keycloakRealmPath: '/auth/realms/fzlbpms',
  keycloakClientId: 'fzlbpmsadmin-web',
};
