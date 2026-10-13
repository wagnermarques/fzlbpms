# 06 — Security, Identity & Networking

> **Project**: `fzlbpms`  
> **Component**: Keycloak 26 SSO, OAuth2-Proxy, TLS Strategies & Network Boundaries  
> **Last Updated**: October 12, 2026

---

## 1. Identity & Access Architecture (Keycloak 26)

Single Sign-On (SSO) and centralized access control are anchored by **Keycloak 26.0.0**, running with a PostgreSQL 16 backing store (`fzl-keycloak-db`).

### The `fzlbpms` Realm Clients Matrix

| Client ID | Access Type | Protocol / Flow | Consumer / Purpose |
|---|---|---|---|
| **`fzlbpms-camel`** | Confidential | Client Credentials / Service Account | Camel backend routes to invoke Keycloak Admin REST API. |
| **`fzlbpmsadmin-web`** | Public | Standard Authorization Code + PKCE | Web Admin SPA (`https://fzlbpms.local/fzlbpmsadmin/`). |
| **`moodle`** | Confidential | Authorization Code | Moodle LMS `auth_oauth2` plugin for student/faculty SSO. |
| **`flowable`** | Confidential | OpenID Connect (Spring Security) | Flowable UI web application and process modeler. |
| **`theia`** | Confidential | Authorization Code | `fzl-oauth2-proxy` to gate Eclipse Theia IDE. |
| **`fzl-chamadasonline`**| Public | Standard Authorization Code + PKCE | Chamadas Online PWA attendance client. |
| **`fzl-tasktodayapp`** | Public | Standard Authorization Code + PKCE | Task Today PWA client. |

---

## 2. Role-Based Access Control (RBAC)

The platform defines fine-grained realm and client roles:

### Realm Roles
- **`admin`**: Full platform administrative privileges; required to trigger organization provisioning and invoke Camel admin BFF endpoints.
- **`theia-user`**: Required by `fzl-oauth2-proxy` to access `/theia/`, `/system/console`, and `/hawtio`.
- **`chamadas-user`**: Granted to enrolled students to submit attendance check-ins.
- **`chamadas-staff`**: Granted to teachers, secretaries, and admins to manage classroom sessions and resolve attendance flags.
- **`tasktoday-admin`**: Authorizes users to create and modify system-wide default task categories.

### Client-Specific Role Mapping (Flowable)
Flowable UI separates privileges into client roles on the `flowable` client:
`access-idm`, `access-admin`, `access-modeler`, `access-task`, and `access-rest-api`.
- A dedicated **protocol mapper** (`flowable_authorities`) embeds these roles into the ID Token claims.
- Only the dedicated `flowable-admin` account is assigned all administrative roles, while regular realm users receive only `access-task`.

### Dedicated Administrative Identities
To prevent single-point credential compromise across disparate software packages:
1. **`fzlbpmsadmin`**: Stack-wide super-administrator (Keycloak master realm & fzlbpms realm admin).
2. **`moodle-admin`**: Dedicated Moodle site administrator account (promoted via SSO).
3. **`flowable-admin`**: Dedicated Flowable BPM administrator account.

---

## 3. Reverse Proxy Forward Authentication (`oauth2-proxy`)

Applications that lack native OpenID Connect modules (such as Eclipse Theia, Apache Felix Web Console, and Hawtio) are protected at the Nginx layer via `fzl-oauth2-proxy` using the **sub-request authentication pattern** (`auth_request`):

```nginx
# Nginx snippet protecting /theia/ and /system/console
location /system/console {
    auth_request /oauth2/auth;
    error_page 401 = @karaf_signin;

    set $karaf_upstream http://fzl-karaf-camel-integration:8181;
    proxy_pass $karaf_upstream;

    # Injects internal HTTP Basic credential from .env
    include /etc/nginx/shared/karaf-auth.conf;
}

location @karaf_signin {
    return 302 /oauth2/start?rd=$request_uri;
}
```

### Transparent Basic Auth Injection
While users authenticate to Keycloak with their SSO credentials, Apache Karaf retains its internal JAAS Basic Authentication. Nginx automatically bridges the two: once `oauth2-proxy` validates the Keycloak session, Nginx injects the `Authorization: Basic <credentials>` header (derived from `FZL_KARAF_USER` and `FZL_KARAF_PASSWORD` in `.env`) before forwarding the request to Karaf.

---

## 4. Dual TLS & Environment Switching

The stack strictly enforces HTTPS because OpenID Connect discovery and Moodle OAuth2 issuer validation reject plain HTTP.

```
       [ Local Development ]                               [ Production ]
      Host: fzlbpms.local                               Host: fzlbpms.com.br
               │                                                 │
      mkcert Root CA installed                          Cloudflare Edge TLS
  Trusted in OS & Container JVMs                                 │
               │                                          Zero Trust Tunnel
    Nginx Port 443 SSL (Local)                            (fzl-cloudflared)
               │                                                 │
               └───────────────► [ fzl-nginx:80 ] ◄──────────────┘
```

### 1. Local Development (`fzlbpms.local`)
- Managed via `ansible/setup-project.yml --tags certs`.
- Uses `mkcert` to issue certificates for `fzlbpms.local`.
- The local root CA is automatically mounted into container certificate stores (`fzl-nginx`, `fzl-php8.3-fpm`, `fzl-flowable-ui`, `fzl-karaf-camel-integration`), ensuring container-to-container HTTPS calls succeed without SSL verification errors.

### 2. Production (`fzlbpms.com.br`)
- Outbound **Cloudflare Zero Trust Tunnel** (`fzl-cloudflared`) connects directly to Cloudflare's network using `FZL_CLOUDFLARE_TUNNEL_TOKEN`.
- Public visitors connect via Cloudflare-managed SSL certificates. Cloudflare forwards plain HTTP traffic over the encrypted tunnel directly to Nginx.
- Nginx preserves client protocols via `X-Forwarded-Proto` and sets `KC_PROXY_HEADERS=xforwarded` on Keycloak.

### 3. Domain Switcher Automation (`bin/switch-domain.sh`)
Switching between local and production requires a single command:
```bash
./bin/switch-domain.sh fzlbpms.local
# or
./bin/switch-domain.sh fzlbpms.com.br
```
This script coordinates:
1. Updating `.env` parameters (`FZL_PUBLIC_HOSTNAME`, `FZL_PUBLIC_PROTO`, `MOODLE_WWWROOT`).
2. Live patching of Moodle's installed `config.php`.
3. Updating Keycloak client redirect URIs via the Keycloak Admin REST API.
4. Restarting `fzl-keycloak`, `fzl-flowable-ui`, and `fzl-oauth2-proxy`.
5. Starting or stopping `fzl-cloudflared`.

---

## 5. Defense-in-Depth Hardening

1. **Keycloak Admin Console Public Isolation**:
   - Recent platform improvements block `/auth/admin/` and `/auth/realms/master/` on public ingress to prevent brute-force attacks from the internet.
   - Access can be toggled dynamically via `settings-view` in `fzlbpmsadmin-web`.
2. **XML-RPC Hardening**:
   - Disabled entirely in Moodle via PHP environment variables and database flags to eliminate legacy amplification and injection vectors.
3. **Secret Generation**:
   - `docker-compose.yml` uses strict `${VAR:?}` syntax.
   - Sensitive credentials (Karaf password, OAuth2 cookie secrets, Chamadas JWT secret) are automatically seeded with high-entropy random strings during initial Ansible setup.
