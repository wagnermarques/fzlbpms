export const environment = {
  apiUrl: '/fzlbpms',
  // Keycloak only ever reports this one fixed hostname (KC_HOSTNAME) in the
  // URLs it generates, regardless of which origin the app itself was loaded
  // from — see keycloak-admin-camel-context.xml's header comment.
  keycloakIssuer: 'https://fzlbpms.com.br/auth/realms/fzlbpms',
  keycloakClientId: 'fzlbpmsadmin-web',
};
