# 02 — Containers & Docker Infrastructure

> **Project**: `fzlbpms`  
> **Component**: Docker Compose Orchestration & Container Matrix  
> **Last Updated**: October 12, 2026

---

## 1. Orchestration Overview

The core stack is defined in `docker-compose.yml` (over 1,000 lines) and orchestrated primarily via `./bin/run-stack.sh`, which parses `run-stack.toml`. All host ports, container identities, and sensitive configuration values are parameterized using strict `${VARIABLE:?}` syntax, ensuring that missing environment variables fail loudly before any container boots.

All inter-container communication occurs over a dedicated Docker bridge network (`fzl-network`), allowing containers to resolve each other by their service names.

---

## 2. Complete Service Matrix

| Service | Base Image / Build | Published Ports (Host → Container) | Primary Purpose | Persistent Storage / Mounts |
|---|---|---|---|---|
| **`fzl-nginx`** | Custom build (`containers/fzl-nginx`) | `80:80`, `443:443`, `8899:8899`, `8900:8900` | Ingress gateway, TLS termination, reverse proxy routing, and FastCGI dispatcher | `containers/fzl-nginx/nginx-conf.d`, `src-projects/var_www/html`, SSL certs |
| **`fzl-php8.3-fpm`** | Custom build (`containers/fzl-php8.3-fpm`) | None (internal `:9000`) | PHP runtime executing Moodle LMS and modern PHP scripts | `src-projects/var_www/html`, `src-projects/moodledata`, SSL certs |
| **`fzl-php8.1-fpm`** | Custom build (`containers/fzl-php8.1-fpm`) | None (internal `:9000`) | Legacy PHP runtime for Yii2 and older applications | `src-projects/var_www/html` |
| **`fzl-postgresql`** | Custom build (`containers/fzl-postgresql`) | `5432:5432` | Relational database hosting `fzldb`, `moodle`, `contatemeantes`, and `orbeon` databases | `containers/fzl-postgresql/postgresql-data` |
| **`fzl-mysql`** | `mysql:8.4.6` | `3316:3306` | Relational database for legacy systems | `containers/fzl-mysql/mysql-data` |
| **`fzl-keycloak`** | `quay.io/keycloak/keycloak:26.0.0` | `8083:8080` | Central Identity and Access Management (SSO, OpenID Connect, OAuth2) | Keycloak DB |
| **`fzl-keycloak-db`** | `postgres:16-alpine` | None (internal `:5432`) | Dedicated database for Keycloak | `containers/fzl-keycloak/postgres-data` |
| **`fzl-oauth2-proxy`**| `quay.io/oauth2-proxy/oauth2-proxy:v7.15.3` | None (internal `:4180`) | Reverse proxy auth gatekeeper protecting Theia, Hawtio, and Felix consoles | SSL certs |
| **`fzl-karaf-camel-integration`** | Custom build (`containers/fzl-karaf-camel-integration`) | `8101:8101`, `8181:8181`, `9090-9091`, `9093-9096` | Integration backbone running OSGi bundles, Camel routes, and REST bridges | `containers/fzl-karaf-camel-integration/karaf-data`, `src-projects/karaf_deploy_dir` |
| **`fzl-flowable-ui`** | `flowable/flowable-ui:6.7.2` | `8080:8080` | BPM engine, Task UI, Identity Management, and Modeler | `src-projects/flowable-processes`, SSL certs |
| **`fzl-flowable-db`** | `postgres:15-alpine` | `5435:5432` | Dedicated database for Flowable BPM | `flowable_db_data` (volume) |
| **`fzl-bpmn-drawer`** | Custom build (`containers/fzl-bpmn-drawer`) | `8085:80` | Web-based BPMN modeling tool | Web assets |
| **`fzl-orbeon`** | Custom build (`containers/fzl-orbeon`) | `8890:8080` | XForms/XHTML visual form runner and form builder for BPMN human tasks | `fzl-postgresql` DB |
| **`fzl-theia`** | Custom build (`containers/fzl-theia`) | `3000:3000` | Browser-based Eclipse Theia IDE with custom BPMN editor plugin | `theia_user_data`, `workspaces` |
| **`fzl-nexus`** | Custom build (`containers/fzl-nexus`) | `8088:8081`, `8082:8082` | Sonatype Nexus 3 Maven repository and Docker registry connector | `containers/fzl-nexus/nexus-data` |
| **`fzl-redis`** | `redis:7-alpine` | `6379:6379` | In-memory cache and queue system (Append-Only File enabled) | `containers/fzl-redis/redis-data` |
| **`fzl-mongodb`** | `mongo:4.4` | `27017:27017` | Document database configured for Overleaf | `containers/fzl-mongodb/mongodb-data` |
| **`fzl-elasticsearch`**| Custom build (`containers/fzl-elasticsearch`) | `9200:9200` | Full-text search and analytical indexing | `containers/fzl-elasticsearch/es-data` |
| **`fzl-kibana`** | Custom build (`containers/fzl-kibana`) | `5601:5601` | Visualization dashboard for Elasticsearch data | Config mounts |
| **`fzl-cloudflared`** | `cloudflare/cloudflared:latest` | None (tunnel client) | Outbound tunnel exposing local Nginx to `https://fzlbpms.com.br` | Tunnel token |
| **`fzl-fakemail`** | `gessnerfl/fake-smtp-server:latest` | Host network (`5080`, `5081`, `8025`) | Development SMTP server and Web UI for inspecting outbound email | In-memory / host ports |
| **`fzl-portainer`** | `portainer/portainer-ce:latest` | `9000:9000`, `9043:9443` | Web UI for inspecting Docker containers, logs, and images | `containers/fzl-portainer/portainer-data`, Docker socket |
| **`fzl-phpmyadmin`** | `phpmyadmin/phpmyadmin` | `8889:80` | Web-based administration tool for MySQL | Config |
| **`gitlab`** | `gitlab/gitlab-ce:16.11.2-ce` | `8086:80`, `2222:22` | Self-hosted Git repository and issue tracker (optional heavy profile) | `gitlab_config`, `gitlab_logs`, `gitlab_data` |

---

## 3. Ephemeral Provisioning Services

Three helper containers act as automated setup runners during the boot of the `basic` profile:
1. **`moodle-installer`**: Checks if Moodle is already installed in PostgreSQL and `var_www/html/moodle`. If not, downloads the specified version, creates config files, and executes Moodle CLI installation.
2. **`moodle-oauth2-configurator`**: Runs after Moodle and Keycloak are up; binds Keycloak as an OpenID Connect OAuth2 Identity Provider inside Moodle via PHP CLI scripts.
3. **`moodle-webservice-configurator`**: Generates and activates Moodle REST web service tokens (`MOODLE_WS_TOKEN`) for the `fzlbpms_ws` service account consumed by Apache Camel.

---

## 4. Runtime Profiles (`run-stack.toml`)

To manage machine resources effectively, `./bin/run-stack.sh` supports selective startup profiles:

```bash
# Start the standard development stack
./bin/run-stack.sh basic

# Start specific concerns
./bin/run-stack.sh web           # Nginx + PHP + DBs
./bin/run-stack.sh integration   # Karaf/Camel + Nexus
./bin/run-stack.sh bpm           # Flowable UI + Flowable DB + BPMN Drawer
./bin/run-stack.sh sso           # Keycloak + Keycloak DB
./bin/run-stack.sh ide           # Eclipse Theia + OAuth2-Proxy
./bin/run-stack.sh forms         # Orbeon Forms
./bin/run-stack.sh search        # Elasticsearch + Kibana
./bin/run-stack.sh devtools      # Portainer, phpMyAdmin, Fakemail, Angular Dev
./bin/run-stack.sh tunnel        # Cloudflare Tunnel

# Stop services for a specific profile
./bin/run-stack.sh --down bpm
```

### Profile Dependency Resolution
Profiles can inherit other profiles. For example:
- **`stacks.basic`**: Inherits `ide` and starts core web, databases, Karaf, Keycloak, Flowable, BPMN drawer, and Orbeon.
- **`stacks.fzlbpms`**: Inherits `web`, `integration`, `bpm`, `sso`, `ide`, `forms`, and adds `fzl-fakemail`.
- **`stacks.all`**: Combines `fzlbpms`, `search`, `devtools`, `gitlab`, and `data-extra`.

---

## 5. Storage Architecture & Backup Hygiene

1. **Host Bind Mounts**: The vast majority of services persist data directly into host folders located under `containers/<service-name>/<data-dir>` or `src-projects/`. This makes local backups straightforward (`tar` or `rsync`) and allows inspection without entering container namespaces.
2. **SELinux Compatibility**: The Ansible playbook (`setup-project.yml`) marks mounted folders with the `container_file_t` SELinux context on Fedora/RHEL hosts.
3. **Isolated Volumes**: Heavy state engines (GitLab, Flowable data) utilize named Docker volumes to prevent performance overhead on copy-on-write host filesystems.
