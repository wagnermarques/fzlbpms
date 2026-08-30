# fzl-orbeon — Orbeon Forms Community Edition

Open-source (LGPL) forms platform: a visual **Form Builder** and a **Form
Runner** for filling forms. Used in this stack as the front door for process
inputs (e.g. the "create turma" start form + CSV upload), decoupled from
Flowable's weak built-in forms.

## Why we build our own image
Orbeon publishes Docker images only for the **commercial PE**. The **Community
Edition** ships as a WAR/ZIP on GitHub releases. This image downloads the CE
ZIP at build time and deploys `orbeon.war` onto **Tomcat 9** (Orbeon 2025.1 is
still `javax.servlet`, so Tomcat 9 — *not* Tomcat 10/Jakarta) on **Java 17**.

## Persistence
Form definitions and submitted data live in **PostgreSQL** (`fzl-postgresql`,
database `orbeon`). `docker-entrypoint-fzl.sh` creates the database and applies
`postgresql-2025_1.sql` (Orbeon's official 2025.1 schema) on first start —
idempotently, so it self-heals on any machine. The DB connection is a Tomcat
JNDI datasource (`jdbc/orbeon`, see `context.xml.template`), pointed at by
`properties-local.xml.template`.

## Access
- Normal: `https://fzlbpms.local/orbeon/` (through nginx).
- Direct dev: `http://localhost:${FZL_ORBEON_PORT}/orbeon/` (bypasses nginx/SSO).
- Landing page: `/orbeon/`; Form Builder: `/orbeon/fr/orbeon/builder/summary`.

## Configuration (.env)
- `FZL_ORBEON_PORT` — host port for direct dev access.
- `FZL_ORBEON_DB_NAME` — Postgres database name (default `orbeon`).
- `FZL_ORBEON_CRYPTO_PASSWORD` — Orbeon's `oxf.crypto.password` (**change it**).
  Reuses `FZL_POSTGRES_USER` / `FZL_POSTGRES_PASSWORD` for the datasource.

## ⚠️ Security — not yet SSO-gated by role
`/orbeon/` is currently protected only by the shared oauth2-proxy sign-in gate
in nginx (a logged-in Keycloak session with the `theia-user` role), the same as
`/theia/`. Form Builder is an **admin tool** — before any real exposure it
should get its **own** Keycloak client + role (e.g. `orbeon-admin`) and,
ideally, Orbeon's own Keycloak/OIDC integration for per-user identity inside
forms. Tracked as a follow-up.
