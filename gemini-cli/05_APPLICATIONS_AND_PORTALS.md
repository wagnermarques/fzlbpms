# 05 — Applications, PWAs & Frontends

> **Project**: `fzlbpms`  
> **Component**: Client Applications, Portals & Web Experiences  
> **Last Updated**: October 12, 2026

---

## 1. Overview

**fzlbpms** serves a diverse ecosystem of client applications, ranging from desktop operating consoles to progressive web apps (PWAs), learning management platforms, and developer IDEs. All applications converge on the same infrastructure for identity, data persistence, and workflow orchestration.

---

## 2. Desktop Administration: `fzlbpmsadmin` (Tauri v2)

Located in `src-projects/fzlbpmsadmin/`:

### Architecture
- **Desktop Framework**: Tauri v2 (native window via WebKitGTK / WebView2).
- **Backend (Rust)**:
  - Docker API integration via `bollard` for container inspection, live log streaming, and startup/shutdown commands.
  - HTTP requests via `reqwest`.
  - Asynchronous event runtime via `tokio`.
  - Log aggregation with `fern`, broadcasting events directly to the frontend.
- **Frontend (Angular)**: Located in `src-projects/fzlbpmsadmin/angular-ui/`, built with modern Angular and Angular Material.
- **Development Tooling**: Provisioned on Debian/Fedora via Ansible (`ansible/tauri-rust-setup.yml`).

### Capabilities
- Lists and manages running Docker containers in real time.
- Manages Moodle installation, site information retrieval, and administration.
- Provides a desktop-native control room for platform operators.

---

## 3. Web Administration: `fzlbpmsadmin-web`

Located in `src-projects/fzlbpmsadmin-web/`:

### Architecture
- **Frontend Framework**: Angular 18+ Single Page Application.
- **Authentication**: Native OpenID Connect using Authorization Code Flow with **PKCE (Proof Key for Code Exchange)** via Keycloak public client `fzlbpmsadmin-web`.
- **URL**: Served via Nginx at `https://fzlbpms.local/fzlbpmsadmin/`.

### Core Views & Modules
1. **Apps Home View (`apps-home-view`)**: Launchpad for all accessible platform services (Moodle, Theia, Flowable, Portainer, Orbeon, etc.).
2. **User Management View (`users-view`)**: Complete administrative UI for Keycloak users (listing, searching, creating, role assignment, password resetting).
3. **Organization Creation View (`organization-create-view`)**: Frontend trigger for Keycloak 26 organization provisioning workflows.
4. **Process Diagram Viewer (`process-diagram-view`)**: Interactive visualization of BPMN process workflows.
5. **Settings View (`settings-view`)**: Platform toggles, including real-time control over whether Keycloak's administrative console is exposed publicly or kept private.

---

## 4. Learning Management: Moodle 5.x

Served via `fzl-nginx` + `fzl-php8.3-fpm` + `fzl-postgresql`:

### Key Features
- **URL**: `https://fzlbpms.local/moodle`.
- **Database**: PostgreSQL database `moodle` on `fzl-postgresql`.
- **Automated Provisioning**:
  - `moodle-installer` automatically downloads and configures Moodle if absent.
  - `moodle-oauth2-configurator` registers Keycloak as the primary OpenID Connect Identity Provider.
  - `moodle-webservice-configurator` provisions the REST web service token used by Camel.
- **Mathematics Focus**: Configured to host mathematics curriculums, with automated scripts for bulk course generation and MathJax integration.
- **Operational CLI Suite**: Over 15 specialized scripts in `bin/moodle/`:
  - `create-db-in-postgresql-container.sh`
  - `moodle-admin-create-course.sh`
  - `moodle-admin-enroll-students-from-csv.sh`
  - `reset-password.sh`
  - `moodle_cache_clean.sh`

---

## 5. Chamadas Online (Attendance Verification PWA)

- **Frontend**: Lightweight mobile-first PWA hosted on GitHub Pages (`https://wagnermarques.github.io/fzl-chamadasonline/`) and local Vite dev servers.
- **Backend**: Camel OSGi bundle `chamadasonline` running on Jetty port 9094, proxied at `https://fzlbpms.local/chamadas/`.
- **User Roles**:
  - `chamadas-user` (Students: perform check-ins, view history).
  - `chamadas-staff` (Teachers & Staff: open events, generate enrollment codes, review suspicious check-ins).
- **Anti-Fraud Engine**:
  - 45s rotating tokens with 90s grace window.
  - Real-time GPS geofencing via Haversine distance and GeoJSON polygons.
  - Hardware device fingerprinting and administrative enrollment codes.

---

## 6. Task Today (Task Management PWA)

- **Frontend**: Modern PWA with offline-first local storage.
- **Backend**: Camel OSGi bundle `tasktoday` running on Jetty port 9095, proxied at `https://fzlbpms.local/api/tasktoday/`.
- **Key Features**:
  - Keycloak RS256 JWT validation against realm JWKS endpoint.
  - Shared native categories (`cat-cursos`, `cat-cotidianas`, `cat-financeira`, `cat-pessoais`) + user-specific custom categories.
  - Deadline filtering (`today`, `overdue`, `this_week`).
  - Native Web Push notification dispatcher (RFC 8291 / RFC 8292 VAPID) triggered by a 30s background alarm loop.

---

## 7. Developer Cloud IDE: Eclipse Theia

Located in `containers/fzl-theia/`:

### Environment & Security
- **URL**: `https://fzlbpms.local/theia/` (or host debug port 3000).
- **Access Control**: Shielded behind `fzl-oauth2-proxy` using Nginx sub-request authentication (`auth_request /oauth2/auth`). Only authenticated users carrying the `theia-user` Keycloak role may access the IDE.
- **Storage**: Bind-mounts host `./workspaces` directly, running as UID/GID 1000 so files created in the browser belong to the host developer.

### Custom BPMN Editor Plugin
- Located in `src-projects/fzl-theia-plugins/theia-plugin-bpmn-editor/`.
- Packaged as a VS Code-compatible VSIX extension.
- Integrates `bpmn-js` to render and edit BPMN XML diagrams directly within the Theia workspace tab without requiring an external desktop tool.

---

## 8. Legacy PHP Applications (Yii2 & ava211)

- **Runtime**: Served via `fzl-php8.1-fpm` and `fzl-mysql`.
- **URL**: Routed through Nginx at `http://localhost/ava211/`.
- **Purpose**: Maintained for legacy compatibility with historical educational systems while newer services migrate to the Keycloak/Camel ecosystem.
