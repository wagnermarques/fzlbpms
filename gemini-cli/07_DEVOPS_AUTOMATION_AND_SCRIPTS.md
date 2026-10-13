# 07 — DevOps, Automation & Operational Tooling

> **Project**: `fzlbpms`  
> **Component**: Ansible Playbooks, CLI Scripts & Maven Templates  
> **Last Updated**: October 12, 2026

---

## 1. Overview

Operational excellence in **fzlbpms** is achieved through full infrastructure-as-code automation. The repository includes automated host onboarding playbooks (Ansible), granular runtime profile managers, environment switchers, and a comprehensive suite of utility scripts.

---

## 2. Ansible Automation Suite (`ansible/`)

The `ansible/` directory automates workstation and production server configuration across **Fedora, Debian 12+, Ubuntu 22.04+, and Alpine**:

### 1. Host Onboarding: `setup-project.yml`
Configures host requirements:
- **Docker Engine & Compose v2**: Official repositories, group permissions (`newgrp docker`).
- **Storage Driver Optimization**: Configures `btrfs` native storage drivers on Fedora.
- **SELinux Permissions**: Applies `container_file_t` to bind-mounted source folders.
- **Local DNS Mapping**: Maps `127.0.0.1 fzlbpms.local` in `/etc/hosts`.
- **Local HTTPS & Trust (`tasks/local-https.yml`)**: Installs `mkcert`, issues `fzlbpms.local` certificates, trusts the root CA in system and browser stores, and mounts certs into containers.
- **Secrets Seeding (`--tags secrets`)**: Generates 28-character cryptographically secure passwords for `FZL_KARAF_PASSWORD` and other unset variables in `.env`.

```bash
# Run complete onboarding
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml -K

# Run certificates step only
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml --tags certs -K
```

### 2. Desktop App Toolchain: `tauri-rust-setup.yml`
Provisions the build environment for the Tauri desktop GUI:
- Installs Rust toolchain via `rustup`.
- Installs system dependencies: `webkit2gtk-4.1`, `GTK3`, `libsoup-3.0`, and `OpenSSL` development headers.

### 3. Chamadas Online Playbooks: `fzl-chamadasonline-setup-dev.yml` & `prod.yml`
Idempotent setup for the attendance platform:
- Configures Keycloak realm roles (`chamadas-user`, `chamadas-staff`).
- Registers public PKCE client with redirect URIs.
- Initializes PostgreSQL tables and schemas in `fzldb`.

### 4. Developer Cleaners: `dev_clean_*.yml`
- `dev_clean_process_definitions_and_instances.yml`: Flushes deployed Flowable definitions and execution history for rapid development resets.
- `dev_clean_flowable_users_groups_and_privs.yml`: Resets Flowable identity tables.

---

## 3. Runtime Management: `bin/run-stack.sh` & `run-stack.toml`

The stack is managed via `./bin/run-stack.sh`, which parses `run-stack.toml` and validates service names against `docker compose config --services`.

### Common Commands
```bash
./bin/run-stack.sh             # Starts the default profile ('web')
./bin/run-stack.sh basic       # Starts full core platform (Web, Karaf, Flowable, Keycloak, Theia, Orbeon)
./bin/run-stack.sh bpm         # Starts BPM engine only (Flowable DB, Flowable UI, BPMN Drawer)
./bin/run-stack.sh --list      # Displays all defined stacks and their service memberships
./bin/run-stack.sh --dry-run   # Prints the docker compose command without running it
./bin/run-stack.sh --down bpm  # Stops containers associated with the bpm profile
```

---

## 4. Operational Utility Toolkit (`bin/`)

The `bin/` directory contains over 40 purpose-built scripts:

### Environment & Domain Management
- **`switch-domain.sh`**: Toggles between `fzlbpms.local` (local development) and `fzlbpms.com.br` (production via Cloudflare Tunnel), updating `.env`, Moodle `config.php`, and Keycloak redirect URIs.
- **`switch-env.sh`**: Switches active `.env` profiles.

### Apache Karaf Operations
- **`karaf-console.sh`**: Opens an interactive SSH session into Karaf OSGi CLI (`bin/client -u karaf`).
- **`tail-karaf-log.sh`**: Streams Karaf container logs in real time.
- **`features-install.sh`** & **`karaf-feature-list.sh`**: Installs/lists OSGi features.
- **`backup-karaf-data.sh`**: Archives Karaf data state.

### Web Server & PHP
- **`nginx-reload.sh`**: Sends `nginx -s reload` to reload configuration without dropping connections.
- **`nginx-tail-log-access.sh`** & **`nginx-tail-log-error.sh`**: Streams Nginx access and error logs.
- **`nginx-permission-appfolder.sh`**: Fixes ownership and permissions on web roots.
- **`php8.3-fpm--exec.sh`**: Executes commands inside the PHP-FPM container.

### Database Operations
- **`psql-connect.sh`** & **`fzl-postgresql-psql.sh`**: Interactive PostgreSQL client connected to `fzldb`.
- **`mysql-bash.sh`** & **`mysql-dump-load.sh`**: MySQL command line and dump loaders.

### Moodle Operations (`bin/moodle/`)
- **`create-db-in-postgresql-container.sh`**: Bootstraps the Moodle PostgreSQL database.
- **`moodle-admin-create-course.sh`**: CLI course generator.
- **`moodle-admin-enroll-students-from-csv.sh`**: Bulk student enrolment runner.
- **`reset-password.sh`**: Resets Moodle admin password directly via PHP CLI.
- **`moodle_cache_clean.sh`**: Flushes Moodle cache directories.

---

## 5. Maven Templates & Archetypes (`src-projects/mvn-artifacts/`)

The platform implements an automated packaging and deployment pipeline for business processes:

```
fzlparent (Nexus 3 Distribution)
    └── fzlflowable-parent (BAR Packaging + REST Deployer)
            └── fzlprocess-archetype (Maven Archetype for Process Authors)
```

1. **`fzlparent`**: Root parent POM (`br.com.fzlbpms:fzlparent:1.0-SNAPSHOT`). Configures distribution management targeting Sonatype Nexus 3 (`http://localhost:8088/repository/maven-releases/`).
2. **`fzlflowable-parent`**: Configures Business Archive (`BAR`) packaging via the Maven Assembly plugin and provides the `deploy-flowable` profile.
3. **`fzlprocess-archetype`**: Generates new workflow projects with sample BPMN definitions, assembly descriptors, and test configurations ready to deploy directly to Flowable:
   ```bash
   mvn verify -Pdeploy-flowable
   ```
