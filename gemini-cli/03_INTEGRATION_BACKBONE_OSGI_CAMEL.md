# 03 — Integration Backbone (OSGi & Apache Camel)

> **Project**: `fzlbpms`  
> **Component**: Apache Karaf 4.4.7 & Apache Camel 3.21 Integration Layer  
> **Last Updated**: October 29, 2026

---

## 1. Overview & Architectural Role

The integration backbone is the central nervous system of **fzlbpms**, hosted in the `fzl-karaf-camel-integration` container. It runs an OSGi runtime powered by **Apache Karaf 4.4.7** on **Eclipse Temurin 21**, with **Apache Camel 3.21** providing Enterprise Integration Patterns (EIP).

Rather than forcing microservices to manage individual database schemas, authentication verifications, and external APIs independently, Camel routes and OSGi bundles act as mediated bridges between frontends, workflow engines, and backend services.

---

## 2. Karaf Container Architecture

The custom Karaf container image (`containers/fzl-karaf-camel-integration`) provides a rich execution environment:
- **Runtime Environment**: Eclipse Temurin JDK 21 on Debian Bookworm.
- **Rootless Execution**: Boots as `root`, runs entrypoint scripts to configure permissions, and drops privileges to `appuser` (UID 1000) using `gosu`.
- **System Utilities**: Bundles Python 3, `ffmpeg` (for media processing), `curl`, `jq`, and the **Docker CLI** (connected to the host's `/var/run/docker.sock`), allowing Camel routes to control containerized tasks directly.
- **Hot Deployment Engine**: The directory `src-projects/karaf_deploy_dir` is bind-mounted directly to `/opt/karaf/deploy`. Any OSGi JAR or Blueprint XML placed in this directory is picked up and deployed within seconds by Apache Felix FileInstall.

### Port Allocation on Karaf

| Port | Protocol / Service | Access Point | Description |
|---|---|---|---|
| **8101** | SSH / Karaf CLI | `bin/karaf-console.sh` | Direct shell into Karaf OSGi console (bundles, features, logs). |
| **8181** | HTTP / Jetty | Reverse proxied via Nginx | Hosts Felix Web Console (`/system/console`) and Hawtio (`/hawtio`). |
| **9090** | HTTP / Jetty | Internal / Nginx `/fzlbpms/` | Keycloak Admin BFF, Auth endpoints, and Orbeon Form bridges. |
| **9091** | HTTP / Jetty | Internal / Flowable | Moodle Admin integration bridge. |
| **9092** | HTTP / Jetty | Nginx `/fzlbpms/aproveitamento/` | Aproveitamento de Disciplinas document generation backend (ODT templates). |
| **9093** | HTTP / Jetty | Direct LAN / Nginx | Contatemeantes GPS device tracking and battery status backend. |
| **9094** | HTTP / Jetty | Nginx `/chamadas/` | Chamadas Online PWA backend (anti-fraud attendance). |
| **9095** | HTTP / Jetty | Nginx `/api/tasktoday/` | Task Today PWA backend + Web Push engine. |
| **9096** | HTTP / Jetty | Internal / Nginx | Evasão Escolar (early dropout prevention) backend. |
| **9097** | HTTP / Jetty | Nginx `/api/bpmndrawer/` | BPMN Drawer diagram persistence and versioning backend. |
| **9098** | HTTP / Jetty | Nginx `/api/orbeon/` | Orbeon Forms catalog and Flowable human task renderer bridge. |
| **1099 / 44444** | JMX / RMI | Internal | Java Management Extensions monitoring. |

---

## 3. Blueprint XML Contexts (`blueprint-xmls-bundles/`)

### A. Keycloak Admin Context (`keycloak-admin-camel-context.xml`)
This context is the largest declarative route definition in the platform (over 2,200 lines). It fulfills two crucial roles:

#### 1. Automated System Bootstrapping (Runs 40s after Karaf starts):
- Verifies and creates the `fzlbpms` Keycloak Realm.
- Creates the `fzlbpms-camel` confidential client (used by backend routes).
- Creates public PKCE clients (`fzlbpmsadmin-web`, `fzl-chamadasonline`, `fzl-tasktodayapp`).
- Creates confidential clients for Moodle (`moodle`) and Flowable (`flowable`).
- Creates the `theia` confidential client and `theia-user` role for `oauth2-proxy`.
- Creates super-admin and application-specific admin accounts (`fzlbpmsadmin`, `moodle-admin`, `flowable-admin`).
- Configures protocol mappers (e.g., mapping Keycloak client roles into Flowable authorities claims).
- Optionally registers Microsoft Entra ID (Azure AD) as an Identity Provider if tenant credentials are provided in `.env`.

#### 2. Backend-for-Frontend (BFF) REST API (Port 9090):
- **Authentication**:
  - `POST /fzlbpms/auth/login` (Resource Owner Password Credentials for Tauri/CLI tools)
  - `POST /fzlbpms/auth/refresh`
  - `POST /fzlbpms/auth/logout`
- **User Management**:
  - `GET /fzlbpms/admin/users` (Paginated list, search)
  - `POST /fzlbpms/admin/users` (Creates user in Keycloak)
  - `PUT /fzlbpms/admin/users/{id}` & `DELETE /fzlbpms/admin/users/{id}`
  - `PUT /fzlbpms/admin/users/{id}/password`
- **Orbeon Form Bridge**:
  - `POST /fzlbpms/admin/create-organization` & `/fzlbpms/admin/roles`
  - Validates `X-Orbeon-Shared-Secret: ${FZL_ORBEON_BRIDGE_SECRET}` on server-to-server submissions.
  - Passes user credentials and starts the corresponding Flowable workflow.

### B. Moodle Admin Context (`moodle-admin-camel-context.xml`)
Operates on Jetty port **9091**. It bridges Flowable BPMN workflow tasks to Moodle's REST Web Service API:
- **Endpoints Exposed**:
  - `POST /fzlbpms/moodle/categories` → Moodle `core_course_create_categories`
  - `POST /fzlbpms/moodle/courses` → Moodle `core_course_create_courses`
  - `POST /fzlbpms/moodle/users` → Moodle `core_user_create_users` (locks auth to `oauth2`)
- **Encoding Protection**: Solves a critical defect in Flowable's HTTP client, which forcibly re-encodes request bodies as ISO-8859-1. Camel accepts pre-URL-encoded UTF-8 strings from Flowable Groovy tasks and passes them directly to Moodle without accent corruption.

### C. File Drop Context (`dir_input_file_processor_blueprint_camel_context.xml`)
Implements an asynchronous file polling route:
- Monitors `containers/fzl-karaf-camel-integration/dir_input`.
- Automatically picks up incoming files, logs metadata, invokes processing steps, and delivers processed output to `dir_output`.

---

## 4. Custom OSGi Bundles (`blueprint-osgi-camel-bundles/`)

### 1. Chamadas Online (`chamadasonline`)
- **Role**: Backend for student attendance tracking with anti-fraud defenses.
- **Port**: Jetty `:9094` (proxied via `https://fzlbpms.local/chamadas/`).
- **Database**: PostgreSQL (`fzldb` on `fzl-postgresql`).
- **Anti-Fraud Mechanics**:
  - **Rotating TOTP Code**: 5-character alphanumeric token rotated every 45 seconds with a 90-second validation grace window.
  - **Haversine Geofencing**: Computes spherical distance between student coordinates and class center; supports GeoJSON Point-in-Polygon checks.
  - **Device Binding**: Mobile devices are identified via `clientToken` and fingerprint hashes; students must enroll their device via a 6-digit one-time code generated by the school administration.
  - **Stealth Mode**: Check-in attempts always return `200 OK` (`{"status":"ok"}`) to the client. Suspect check-ins (outside geofence, wrong device) are secretly flagged in the database for staff review.

### 2. Task Today (`tasktoday`)
- **Role**: Backend for the Task Today PWA (categories, task deadlines, and push alerts).
- **Port**: Jetty `:9095` (proxied via `https://fzlbpms.local/api/tasktoday/`).
- **Security**: Verifies incoming `Authorization: Bearer <token>` RS256 JWTs directly against Keycloak's JWKS endpoint with in-memory caching. Restricts native category manipulation to callers holding the `tasktoday-admin` realm role.
- **Web Push Engine**: Implements RFC 8291 (`aes128gcm`) encryption and RFC 8292 (`VAPID ES256`) using native Java Cryptography Architecture (JCA/JCE) without bulky external dependencies like BouncyCastle.
- **Alarm Engine**: An internal 30-second timer route inspects pending deadlines in PostgreSQL and dispatches Web Push alerts to active browser subscriptions.

### 3. Evasão Escolar (`evasaoescolar`)
- **Role**: Backend service for monitoring student academic risk and school dropout prevention.
- **Port**: Jetty `:9096` (proxied via `https://fzlbpms.local/api/evasaoescolar/`).
- **Database**: PostgreSQL (`fzldb` on `fzl-postgresql`), tables `evasao_classes`, `evasao_students`, `evasao_attendance`, `evasao_grades`, `evasao_risk_evaluations`, `evasao_interventions`.
- **Security**: Validates incoming Keycloak RS256 JWT tokens against realm JWKS (public PKCE client `evasaoescolar`).
- **Risk Engine**: Automated heuristic evaluating attendance rates (<75%), consecutive absences (>=5 days), and failing grades (<6.0), classifying students into `BAIXO`, `MEDIO`, `ALTO`, and `CRITICO` risk levels.
- **API Endpoints**: Dashboard stats (`/dashboard/stats`, `/dashboard/alerts`), classes, students CRUD, attendance tracking, grades logging, and pedagogical interventions management.

### 4. Processador de Arquivos (`processadordearquivos`)
- **Role**: Background media and document conversion bundle executing native Linux utilities (`ffmpeg`, `yt-dlp`) through Camel Processors. Consumes URL list files (`*.urls`, `*.txt`) from `/dir_input/` and invokes `yrdlp.sh`.

### 5. BPMN Drawer (`bpmndrawer`)
- **Role**: Backend REST service for persisting and versioning BPMN 2.0 XML diagram files authored in the frontend modeler.
- **Port**: Jetty `:9097` (proxied via `https://fzlbpms.local/api/bpmndrawer/`).
- **Database**: PostgreSQL (`fzldb` on `fzl-postgresql`), table `bpmndrawer_diagrams`.
- **Security**: Nginx `auth_request` gate against `fzl-oauth2-proxy` (role `theia-user`). Confies in the inbound `X-Forwarded-User` header.
- **Endpoints**: `/health`, `/diagrams` (GET/POST), `/diagrams/{id}` (GET/PUT/DELETE).

### 6. Orbeon Forms Shared Bridge (`orbeonforms`)
- **Role**: Shared REST backend that connects the platform with the Orbeon Forms runtime and bridges human task management with Flowable BPMN.
- **Port**: Jetty `:9098` (proxied via `https://fzlbpms.local/api/orbeon/`).
- **Database**: PostgreSQL (`orbeon` database on `fzl-postgresql`), reading published form definitions and schemas.
- **Security**: Validates incoming `X-Forwarded-User` from Nginx or `X-Orbeon-Shared-Secret` on server-to-server calls.
- **Endpoints**:
  - `/forms` (GET/POST) & `/forms/{app}/{form}`: Lists and queries published Orbeon Form definitions.
  - `/tasks` & `/tasks/{taskId}/open` / `complete`: Lists and interacts with user human tasks from Flowable, embedding Orbeon Form Runner URLs.

### 7. Contatemeantes (`contatemeantes`)
- **Role**: Backend for GPS telemetry, device battery monitoring, and geolocation tracking of connected field devices.
- **Port**: Jetty `:9093` (direct LAN access / reverse proxied via Nginx).
- **Database**: PostgreSQL (`fzl-postgresql`), tables `device_status`, `device_groups`.
- **Endpoints**: `/contatemeantes/ping`, `/location` (POST telemetry), `/device/{deviceId}`, `/group/{groupId}/devices`, `/groups`, `/group/{groupId}/locations`.

### 8. Aproveitamento de Disciplinas (`etec_coord_processo__aproveitamento_disciplina` / `aproveitamento-docs`)
- **Role**: Document generation engine for academic course equivalence processes in vocational education (ETEC). Generates standardized OpenDocument (`.odt`) committee opinions and dispatches from templates (`Doc_13_1_anexo_I...odt` and `Doc_13_2_anexo_II...odt`).
- **Port**: Jetty `:9092` (proxied via `https://fzlbpms.local/fzlbpms/aproveitamento/`).
- **Endpoints**:
  - `GET /fzlbpms/aproveitamento/components`: Returns curriculum components catalog.
  - `POST /fzlbpms/aproveitamento/generate`: Accepts student, teachers, and decision payload to assemble and emit finalized `.odt` documents.

---

## 5. Hot Deployment & Development Cycle

The developer workflow for modifying or adding OSGi bundles is streamlined:

```bash
# 1. Compile bundle via Maven Bundle Plugin
mvn clean install -DskipTests -f src-projects/karaf_bundles/blueprint-osgi-camel-bundles/<bundle>/pom.xml

# 2. Deploy to Karaf
# Maven resources plugin outputs directly to src-projects/karaf_deploy_dir/
# Karaf Felix FileInstall detects the JAR file update within 2-5 seconds

# 3. Synchronize Blueprint XML Contexts
# When modifying files in blueprint-xmls-bundles/, synchronize them to deploy dir:
./bin/sync-karaf-deploy.sh

# 4. Inspect deployment status inside Karaf
./bin/tail-karaf-log.sh
```
