# 03 — Integration Backbone (OSGi & Apache Camel)

> **Project**: `fzlbpms`  
> **Component**: Apache Karaf 4.4.7 & Apache Camel 3.21 Integration Layer  
> **Last Updated**: October 12, 2026

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
| **9094** | HTTP / Jetty | Nginx `/chamadas/` | Chamadas Online PWA backend. |
| **9095** | HTTP / Jetty | Nginx `/api/tasktoday/` | Task Today PWA backend + Web Push engine. |
| **9096** | HTTP / Jetty | Internal / Nginx | Evasão Escolar (early dropout prevention) backend. |
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
- **Role**: Background media and document conversion bundle executing native Linux utilities (`ffmpeg`, `imagemagick`, pandoc) through Camel Processors.

---

## 5. Hot Deployment & Development Cycle

The developer workflow for modifying or adding OSGi bundles is streamlined:

```bash
# 1. Compile bundle via Maven Bundle Plugin
mvn clean install -f src-projects/karaf_bundles/blueprint-osgi-camel-bundles/<bundle>/pom.xml

# 2. Deploy to Karaf
# Maven outputs directly to src-projects/karaf_deploy_dir/
# Karaf FileInstall detects the JAR file update within 2-5 seconds

# 3. Inspect deployment status inside Karaf
./bin/karaf-console.sh
bundle:list | grep -i <bundle>
log:tail
```
