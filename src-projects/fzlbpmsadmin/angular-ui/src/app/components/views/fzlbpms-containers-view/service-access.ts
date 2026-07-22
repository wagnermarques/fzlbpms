import { open } from '@tauri-apps/plugin-shell';

// window.open is a silent no-op inside the Tauri webview; the shell plugin
// opens the URL in the user's default browser. Fall back to window.open so
// links still work under plain `ng serve`.
export async function openExternal(url: string) {
  try {
    await open(url);
  } catch (error) {
    console.error(`shell open failed for ${url}, falling back to window.open:`, error);
    window.open(url, '_blank');
  }
}

export interface ServiceLink {
  label: string;
  url: string;
}

export interface ServiceAccess {
  links: ServiceLink[];
  info: string[];
}

// Host ports/credentials mirror the repo's .env file (the single source of
// truth for the compose stack). If a FZL_*_PORT changes there, update here.
const SERVICE_ACCESS: Record<string, ServiceAccess> = {
  // Contexts mirror containers/fzl-nginx/nginx-conf.d/00-catchall.conf — keep in sync.
  'fzl-nginx': {
    links: [
      { label: 'Root PHP apps — /', url: 'http://localhost' },
      { label: 'Moodle — /moodle', url: 'http://localhost/moodle' },
      { label: 'AVA 211 — /ava211', url: 'http://localhost/ava211/' },
      { label: 'Keycloak — /auth', url: 'http://localhost/auth/' },
      { label: 'Karaf console — /karafconsole', url: 'http://localhost/karafconsole/' },
    ],
    info: [
      'Single server on port 80 (00-catchall.conf): any folder under src-projects/var_www/html is served as a PHP app via fzl-php8.3-fpm; /auth and /karafconsole are reverse-proxied to Keycloak and Karaf.',
    ],
  },
  'fzl-php8.1-fpm': {
    links: [],
    info: [
      'Internal PHP-FPM (FastCGI) backend used by fzl-nginx — no browser access.',
      'Shell: docker exec -it fzl-php8.1-fpm bash',
    ],
  },
  'fzl-php8.3-fpm': {
    links: [],
    info: [
      'Internal PHP-FPM (FastCGI) backend used by fzl-nginx — no browser access.',
      'Shell: docker exec -it fzl-php8.3-fpm bash',
    ],
  },
  'fzl-mysql': {
    links: [{ label: 'phpMyAdmin', url: 'http://localhost:8889' }],
    info: ['CLI: mysql -h 127.0.0.1 -P 3316 -u fzl_user -p (or root)'],
  },
  'fzl-postgresql': {
    links: [],
    info: [
      'CLI: psql -h localhost -p 5432 -U postgres',
      'Shell: docker exec -it fzl-postgresql psql -U postgres',
    ],
  },
  'fzl-karaf-camel-integration': {
    links: [
      { label: 'Users API — list all', url: 'http://localhost:9090/fzlbpms/admin/users' },
    ],
    info: [
      'REST API base: http://localhost:9090/fzlbpms/admin/users',
      'GET /users · GET /users/{id} · POST /users · PUT /users/{id} · DELETE /users/{id}',
      'Karaf console: docker exec -it fzl-karaf-camel-integration /opt/karaf/bin/client',
      'Web console gateway: http://localhost/karafconsole/ (503/500 until the Karaf webconsole feature is installed).',
    ],
  },
  'fzl-angular-dev': {
    links: [{ label: 'Dev server', url: 'http://localhost:4200' }],
    info: [],
  },
  'fzl-nexus': {
    links: [{ label: 'Nexus UI', url: 'http://localhost:8088' }],
    info: ['Docker registry: docker login localhost:8082'],
  },
  'fzl-phpmyadmin': {
    links: [{ label: 'phpMyAdmin', url: 'http://localhost:8889' }],
    info: [],
  },
  'fzl-elasticsearch': {
    links: [{ label: 'REST API (JSON)', url: 'http://localhost:9200' }],
    info: ['API only — use Kibana (port 5601) for a graphical UI.'],
  },
  'fzl-kibana': {
    links: [{ label: 'Kibana', url: 'http://localhost:5601' }],
    info: [],
  },
  'fzl-mongodb': {
    links: [],
    info: [
      'Port not published on the host — no browser access.',
      'Shell: docker exec -it fzl-mongodb mongosh',
    ],
  },
  'fzl-redis': {
    links: [],
    info: [
      'Port not published on the host — no browser access.',
      'CLI: docker exec -it fzl-redis redis-cli',
    ],
  },
  'fzl-fakemail': {
    links: [{ label: 'Mail web UI', url: 'http://localhost:5080' }],
    info: [
      'SMTP for the apps: localhost:8025',
      'Management API: http://localhost:5081',
    ],
  },
  'fzl-portainer': {
    links: [
      { label: 'Portainer (HTTP)', url: 'http://localhost:9000' },
      { label: 'Portainer (HTTPS)', url: 'https://localhost:9043' },
    ],
    info: [],
  },
  'fzl-keycloak-db': {
    links: [],
    info: [
      'Internal PostgreSQL for Keycloak — port not published, no browser access.',
      'Shell: docker exec -it fzl-keycloak-db psql -U keycloak keycloak',
    ],
  },
  'fzl-keycloak': {
    links: [
      { label: 'Admin console (gateway)', url: 'http://localhost/auth/admin' },
      { label: 'Admin console (direct)', url: 'http://localhost:8083/auth/admin' },
      { label: 'REST Admin API docs (Swagger UI)', url: 'https://www.keycloak.org/docs-api/latest/rest-api/index.html' },
    ],
    info: [
      'REST Admin API base: http://localhost:8083/auth/admin/realms (requires Bearer token — use the OpenAPI spec at https://www.keycloak.org/docs-api/latest/rest-api/openapi.json to import into Postman/Insomnia).',
      'Get a token: curl -s -d "client_id=admin-cli&grant_type=password&username=<user>&password=<pass>" http://localhost:8083/auth/realms/master/protocol/openid-connect/token | jq .access_token',
    ],
  },
  'fzl-bpmn-drawer': {
    links: [{ label: 'BPMN Drawer', url: 'http://localhost:8085' }],
    info: [],
  },
  'flowable-postgres': {
    links: [],
    info: [
      'CLI: psql -h localhost -p 5435 -U flowable',
      'Shell: docker exec -it flowable-postgres psql -U flowable',
    ],
  },
  'flowable-ui': {
    links: [{ label: 'Flowable UI', url: 'http://localhost:8080/flowable-ui' }],
    info: [],
  },
  'gitlab-ce': {
    links: [{ label: 'GitLab', url: 'http://localhost:8086' }],
    info: [
      'Git over SSH: ssh://git@localhost:2222',
      'Initial root password: docker exec -it gitlab-ce grep Password: /etc/gitlab/initial_root_password',
    ],
  },
};

// Longest key wins so e.g. 'fzl-keycloak-db' is not shadowed by 'fzl-keycloak'.
export function getServiceAccess(containerName: string): ServiceAccess | undefined {
  const key = Object.keys(SERVICE_ACCESS)
    .filter(k => containerName.includes(k))
    .sort((a, b) => b.length - a.length)[0];
  return key ? SERVICE_ACCESS[key] : undefined;
}
