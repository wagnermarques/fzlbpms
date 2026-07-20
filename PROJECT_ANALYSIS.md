# fzlbpms — Project Analysis

*Last updated: 2026-07-19*

## 1. Purpose and Vision

**fzlbpms** (FZL Business Process Management System) is a container-based platform for
designing, deploying and running business processes. The stated goals of the project are:

1. **Integration backbone** — an OSGi / Apache Karaf / Apache Camel runtime where
   integration bundles (Blueprint + Camel routes) are deployed as the "nervous system"
   of the platform.
2. **Software ecosystem around the backbone** — supporting services such as Keycloak
   (single sign-on for all apps), Nexus (artifact repository), GitLab (source and issue
   tracking), Elasticsearch/Kibana, Redis, MongoDB, mail testing, Portainer, etc.
3. **Maven templates for business processes** — a set of parent POMs / archetypes
   (`src-projects/mvn-artifacts/`) that bootstrap new business-process projects whose
   instances run in **Flowable** (chosen as the BPM engine; Camunda was evaluated and
   removed on 2026-07-19 because its kernel is no longer open source).
4. **Moodle for mathematical courses** — a Moodle instance served by the
   Nginx + PHP-FPM containers, managed by a rich set of CLI scripts, intended to host
   mathematics courses inside this same infrastructure.

## 2. Repository Layout

```
fzlbpms/
├── docker-compose.yml          # Orchestration of the whole stack (~20 services)
├── docker-compose-up.sh        # Convenience startup script
├── .env.template               # Environment variable template
├── bin/                        # 40+ operational shell scripts (see §6)
│   ├── moodle/                 # Moodle admin scripts (install, courses, enrolment, backup…)
│   ├── yii2apps/               # Yii2 application helpers
│   └── fzlbpms-runtime-profiles/
├── containers/                 # One directory per custom container image
├── src-projects/               # Source code and mounted work directories
│   ├── fzlbpmsadmin/           # Tauri v2 desktop admin GUI (Rust + Angular)
│   ├── mvn-artifacts/          # Maven parent POMs / templates
│   ├── karaf_bundles/          # OSGi bundles & Blueprint XMLs deployed to Karaf
│   ├── karaf_deploy_dir/       # Hot-deploy dir mounted at /opt/karaf/deploy
│   ├── karaf_input_dir/        # Camel input directory (file routes)
│   ├── karaf_output_dir/       # Camel output directory
│   ├── karaf_project_sources_dir/
│   ├── var_www/html/           # Web root served by Nginx (Moodle, Yii2 apps…)
│   └── moodledata/             # Moodle data root (mounted into nginx & php-fpm)
├── applications/moodle/        # Ansible-style task files for Moodle requirements
├── workspaces/camunda/         # BPMN diagrams (e.g. diagram-fluxo-auth.bpmn) — portable to Flowable
├── documentation/              # Org-mode docs (install, moodle, osgi-blueprint, jakarta-ee…)
├── uml/                        # Use-case and activity diagrams
└── restclient-moodle-rest-api.rest  # REST client requests for the Moodle web-service API
```

## 3. Container Stack (docker-compose.yml)

### Integration backbone
| Service | Image / Build | Ports | Notes |
|---|---|---|---|
| `fzl-karaf-camel-integration` | Custom (Temurin 21 + Karaf 4.4.7) | 8101 (ssh), 8181 (webconsole), 1099, 44444 (not published) | Hot-deploy via mounted `karaf_deploy_dir`; custom `etc/` config; has Docker CLI inside (docker-outside-of-docker via gosu/appuser) |

The Karaf image is notable: it bundles Python 3, ffmpeg, build tools and the Docker CLI,
suggesting Camel routes that shell out to media processing and container orchestration.
One sample bundle exists: `blueprint-osgi-camel-bundles/processadordearquivos` plus a
Blueprint XML file route (`dir_input_file_processor_blueprint_camel_context.xml`) that
processes files dropped in `karaf_input_dir`.

### BPM engine
| Service | Image | Ports | Notes |
|---|---|---|---|
| `flowable-ui` + `flowable-db` | `flowable/flowable-ui:6.7.2` + postgres:15 | 8080, db on 5435 | **Pinned to 6.7.2** — 6.8.0 has a `DefaultAsyncTaskExecutor` NullPointerException on task execution |
| `fzl-bpmn-drawer` | Custom build | 8085 | BPMN modelling web app |

**Flowable is the chosen engine.** Camunda (service, container image and helper scripts)
was removed on 2026-07-19 since its kernel became proprietary and this project targets
open-source tools. The BPMN diagrams in `workspaces/camunda/` were kept — BPMN 2.0 XML
is portable to Flowable.

### Identity / SSO
| Service | Image | Ports | Notes |
|---|---|---|---|
| `fzl-keycloak` | `quay.io/keycloak/keycloak:26.0.0` | 8083 (`/auth` relative path) | start-dev mode, admin/admin, backed by dedicated `fzl-keycloak-db` (postgres:16-alpine) |

Keycloak is up but **no clients/realms are versioned in the repo yet** — integration with
Moodle (OIDC plugin), Flowable/Camunda, GitLab and the admin GUI is still to be wired.
`workspaces/camunda/diagram-fluxo-auth.bpmn` suggests the auth flow is being modelled.

### Web / PHP layer (Moodle & Yii2 apps)
| Service | Ports | Notes |
|---|---|---|
| `fzl-nginx` | 80 | Single catchall conf serves any folder in `var_www/html`; proxies `/auth` (Keycloak) and `/karafconsole` (Karaf); Moodle at `http://localhost/moodle` |
| `fzl-php8.3-fpm` | — | Runs Moodle (XMLRPC disabled via env vars) |
| `fzl-php8.1-fpm` | — | Legacy PHP apps (Yii2 / ava211) |
| `fzl-mysql` (8.4.6) | 3316→3306 | + `fzl-phpmyadmin` on 8889 |
| `fzl-postgresql` | 5432 | pt_BR.UTF-8 locale, init scripts dir; Moodle DB is created here (`bin/moodle/create-db-in-postgresql-container.sh`) |

### Development & platform services
| Service | Ports | Purpose |
|---|---|---|
| `fzl-nexus` | 8088→8081, 8082 | Maven artifact repository (deploy target of the fzl parent POMs) |
| `gitlab` (16.11.2-ce) | 8086, ssh 2222 | Local source hosting / issue tracking |
| `fzl-angular-dev` | 4200 | Angular dev container |
| `fzl-elasticsearch` + `fzl-kibana` | 9200 / 5601 | Search & dashboards (x-pack security on) |
| `fzl-mongodb` (4.4) | — | Configured for Overleaf (overleaf user/db) |
| `fzl-redis` | — | Cache/queues (AOF enabled) |
| `fzl-fakemail` | host network | SMTP testing |
| `fzl-portainer` | 9000, 9043 | Container management UI |

Other container definitions exist but are not (yet) in compose: `fzl-rust-restservices`,
`fzl-ia-sdks-python`, `emacs`.

## 4. Maven Templates (`src-projects/mvn-artifacts/`)

| Artifact | Purpose |
|---|---|
| `fzlparent` | Parent POM (`br.com.fzlbpms:fzlparent:1.0-SNAPSHOT`), configures deployment to the local **Nexus** (nexus-staging-maven-plugin, snapshots repo at `http://localhost:8081/nexus/...`) |
| `fzlparent_m2t` | Parent variant — model-to-text / template generation |
| `fzlparent_oo` | Parent variant — OO projects |
| `fzloo_extension` | Maven extension |

These are the seeds of the "maven templates to create a business process project" goal.
`fzlparent` now targets the **Nexus 3** repository layout (`/repository/maven-releases/`
and `/repository/maven-snapshots/`) at `localhost:8088` via the `fzl.nexus.host` /
`fzl.nexus.port` properties, which mirror `FZL_NEXUS_PORT_HTTP` in `.env` and can be
overridden with `-Dfzl.nexus.port=...` (use host `fzl-nexus`, port 8081 when building
inside the compose network).
**Gap:** none of them yet generates a Flowable-ready project (process definitions,
`flowable.*` config, deployable BAR/JAR).

## 5. Admin GUI (`src-projects/fzlbpmsadmin`)

A **Tauri v2** desktop application (VS Code-like architecture):

- **Backend (Rust)**: `tokio`, `reqwest`, `bollard` (Docker API), `fern` logging with
  events forwarded to the UI. Modules: `cmd.rs` (incl. `install_moodle`),
  `containers*.rs` (list/logs/compose up), `projects*.rs`, `moodle_api.rs` (Moodle REST
  wrapper).
- **Frontend (Angular ~v20 + Angular Material)** in `angular-ui/`, run with
  `npm run tauri:dev`, built with `npm run tauri:build`.
- Exposed commands include `fzlbpms_version`, `get_fzlbpms_home`, `get_site_info`,
  `list_running_containers`, `list_projects`, `install_moodle`.

The GUI is effectively the operator console for the whole platform: container control,
project listing, and Moodle installation/administration through its REST API
(`restclient-moodle-rest-api.rest` documents the calls being used).

## 6. Moodle (mathematical courses)

Moodle runs on the Nginx + PHP 8.3-FPM pair with data in `src-projects/moodledata` and
its database in `fzl-postgresql`. Operational tooling is already substantial
(`bin/moodle/`):

- `create-db-in-postgresql-container.sh`, `moodledata-create-dir.sh`, install docs in
  `documentation/moodle_installations.org`
- Course/user management: `moodle-admin-create-course.sh`,
  `moodle-admin-enroll-students-from-csv.sh`, `moodle-users-list.sh`,
  `reset-password.sh`
- Maintenance: `moodledata-backup.sh`, `moodle_cache_clean.sh`, `cron-config.sh`,
  XMLRPC hardening (`plugin_xmlrpc_disable_in_db.sh`, compose env vars)
- Dev config: `moodle-config-php-dev.php`

Access: `http://localhost/moodle` (admin user reset via
`./bin/moodle/reset-password.sh`).

**For mathematical courses specifically**, the missing pieces are typical Moodle math
add-ons: MathJax filter (bundled, needs enabling), and optionally **STACK** (maths
question type, requires a **Maxima** backend — could be another `fzl-*` container),
GeoGebra, and WIRIS filters. None are provisioned yet.

## 7. Operational Scripts (`bin/`)

Grouped by concern:
- **Karaf**: `karaf-console.sh`, `karaf-webconsole.sh`, `karaf-feature-list.sh`,
  `karaf-repo-add.sh/-list.sh`, `features-install.sh`, `tail-karaf-log.sh`,
  `backup-karaf-data.sh`
- **Nginx/PHP**: reload, logs, permissions, site listing, php-fpm exec/logs
- **Databases**: `psql-connect.sh`, `fzl-postgresql-*`, `mysql-bash.sh`,
  `mysql-dump-load.sh`
- **Platform**: `docker-compose-rebuild-service.sh`, `fzlbpms-fedora-install.sh`,
  `utils.sh`, runtime profiles

## 8. Strengths

- Full local platform with one `docker compose up`: BPM, SSO, artifacts, git hosting,
  search, mail testing, admin UIs.
- Data persisted via bind mounts under `containers/*/`-data dirs — easy to inspect and
  back up; Karaf hot-deploy loop (`karaf_bundles` → `deploy`) makes bundle iteration fast.
- Good operational script coverage, especially for Moodle and Karaf.
- Documentation habit (org-mode docs + UML) and a custom desktop admin GUI.

## 9. Decisions Taken (2026-07-19)

- **Camunda removed** (service, `containers/fzl-camunda-server/`, `bin/camunda-*.sh`):
  its kernel is no longer open source; **Flowable** is the project's BPM engine.
- **`.env` is the single source of truth** for host ports and credentials.
  `docker-compose.yml` uses `${VAR:?}` substitution (fails loudly if a variable is
  missing); `.env.template` is versioned, `.env` is gitignored and holds real values.
- **`fzlparent` fixed for Nexus 3** — correct port (8088) and repository paths
  (`/repository/maven-*/`); the Nexus 2-only `nexus-staging-maven-plugin` was dropped
  (plain `mvn deploy` works with Nexus 3). The Nexus container healthcheck was also
  fixed to use the internal port and the Nexus 3 status endpoint.

## 10. Gaps & Risks (recommended next steps)

1. **Keycloak integration not wired** *(deliberately postponed)* — no realm export in
   the repo. When ready: create an `fzl` realm, export it to
   `containers/fzl-keycloak/realm-export/` and import on startup (`--import-realm`);
   then connect Moodle (OpenID Connect plugin), GitLab (omnibus OIDC), Flowable and the
   admin GUI.
2. **Maven templates incomplete** — evolve `fzlparent*` into real **archetypes** that
   generate: (a) an OSGi/Camel Blueprint bundle project, and (b) a Flowable process
   project (BPMN + service tasks + deployment).
3. **Moodle math stack** *(deliberately postponed)* — enable MathJax; consider a
   Maxima/STACK container for real mathematics assessment; add these to
   `applications/moodle/` provisioning tasks.
4. **Port sprawl** — ~15 published host ports (now all listed in `.env`) with some
   historical conflicts already worked around (Flowable db on 5435, GitLab on 8086).
   Consider routing web UIs through `fzl-nginx` virtual hosts to reduce collisions and
   enable SSO-friendly hostnames.
5. **Resource weight** — GitLab + Nexus + Elasticsearch + Flowable + Keycloak is heavy
   for one machine; the `bin/fzlbpms-runtime-profiles` idea (start subsets per task) is
   the right direction and worth finishing.
6. **Project hygiene** — README quick-start references an older path layout; Karaf
   `custom_karaf_etc` pinned to 4.4.7 (keep in sync when upgrading Karaf).

## 11. Quick Start (current state)

```bash
# First time only: create your local env file (single source of ports/secrets)
cp .env.template .env   # then edit values

# Core web + db
docker compose up --build fzl-nginx fzl-php8.3-fpm fzl-postgresql

# Integration backbone
docker compose up --build fzl-karaf-camel-integration fzl-nexus

# BPM + SSO
docker compose up flowable-db flowable-ui fzl-keycloak-db fzl-keycloak

# Admin GUI (host)
cd src-projects/fzlbpmsadmin/angular-ui && npm install && npm run tauri:dev
```

Key URLs (default ports from `.env`): Moodle `:80/moodle` · Flowable `:8080` ·
Keycloak `:8083/auth` · BPMN drawer `:8085` · GitLab `:8086` · Nexus `:8088` · Karaf
console `:8181/system/console` · Kibana `:5601` · Portainer `:9000` · phpMyAdmin `:8889`.
