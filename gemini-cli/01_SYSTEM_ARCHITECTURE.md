# 01 — System Architecture & Core Concepts

> **Project**: `fzlbpms`  
> **Component**: Architectural Topology & Structural Patterns  
> **Last Updated**: October 12, 2026

---

## 1. Executive Overview

**fzlbpms** is designed as a unified, service-oriented Business Process Management System (BPMS) platform. Rather than relying on a single monolithic enterprise application, fzlbpms adopts an ecosystem approach: it stitches together specialized, best-in-breed open-source tools via an OSGi/Camel integration backbone and a unified Keycloak Single Sign-On (SSO) realm.

The platform orchestrates workflows across human participants (via Orbeon Forms and Angular portals), automated services (via Apache Camel and REST APIs), and external educational systems (such as Moodle LMS).

---

## 2. Core Architectural Pillars

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       1. GATEWAY & INGRESS LAYER                        │
│   Nginx Reverse Proxy • mkcert Local TLS • Cloudflare Tunnel Ingress     │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                    2. IDENTITY & ACCESS MANAGEMENT                      │
│   Keycloak 26 (Realm 'fzlbpms') • OAuth2-Proxy • OpenID Connect / PKCE   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                   3. INTEGRATION BACKBONE (NERVOUS SYSTEM)              │
│   Apache Karaf 4.4.7 (OSGi) • Apache Camel 3.21 • Blueprint DSL         │
│   BFF Routes • Hot-Deploy Bundles • Bridge Endpoints                     │
└──────────────┬───────────────────────────────────────────┬──────────────┘
               │                                           │
┌──────────────┴──────────────┐             ┌──────────────┴──────────────┐
│  4. BPM & WORKFLOW ENGINE   │             │   5. APPLICATIONS & ED-TECH │
│   Flowable 6.7.2 Engine     │             │   Moodle 5.x LMS (PHP 8.3)  │
│   BPMN 2.0 XML Definitions  │             │   Chamadas Online (PWA)     │
│   Orbeon Forms Human Tasks  │             │   Task Today (PWA + Push)   │
│   BPMN Drawer & Theia IDE   │             │   fzlbpmsadmin (Web/Desktop)│
└─────────────────────────────┘             └─────────────────────────────┘
```

### Pillar 1: Gateway & Ingress Layer
- **Unified Entrypoint**: All external HTTP/HTTPS traffic enters through `fzl-nginx`.
- **Zero Port Collisions**: Instead of requiring clients to access numerous custom host ports, Nginx provides path-based reverse proxying:
  - `/moodle` → PHP 8.3-FPM FastCGI
  - `/fzlbpmsadmin/` → Angular Web Admin SPA
  - `/chamadas/` → Chamadas Online Camel bundle (port 9094)
  - `/api/tasktoday/` → Task Today Camel bundle (port 9095)
  - `/orbeon/` → Orbeon Form Runner / Builder (port 8080 internal)
  - `/theia/` → Eclipse Theia Browser IDE (guarded by `oauth2-proxy`)
  - `/system/console` & `/hawtio` → Karaf administrative consoles (guarded by `oauth2-proxy`)
  - `/auth/` → Keycloak Identity Provider (port 8080 internal)
  - `/flowable-ui/` → Flowable UI BPM suite (port 8080 internal)
- **Dual Environment Switching**: Seamless transition between local development (`fzlbpms.local` with mkcert certificates) and production (`fzlbpms.com.br` with Cloudflare Zero Trust Tunnel) using `bin/switch-domain.sh`.

### Pillar 2: Unified Identity & Access Management (IAM)
- **Keycloak 26**: The single authority for authentication and authorization.
- **Automated Realm Bootstrapping**: Keycloak is not manually clicked through; the Camel Blueprint route `keycloak-admin-camel-context.xml` automatically initializes the `fzlbpms` realm, clients, roles, and administrative users upon system startup.
- **Protocols Supported**:
  - **OpenID Connect (Authorization Code + PKCE)**: Used by SPAs (`fzlbpmsadmin-web`, `fzl-chamadasonline`, `fzl-tasktodayapp`).
  - **Confidential Client OAuth2**: Used by Moodle (`auth_oauth2` plugin) and Flowable UI.
  - **Forward Authentication / Sub-request Auth**: Handled by `fzl-oauth2-proxy` using Nginx `auth_request` for tools that lack native Keycloak plugins (Theia, Hawtio, Apache Felix console).

### Pillar 3: Integration Backbone (Apache Karaf & Camel)
- **Role**: Acts as the central integration "nervous system".
- **OSGi Runtime**: Apache Karaf 4.4.7 running on Eclipse Temurin 21.
- **Enterprise Integration Patterns (EIP)**: Powered by Apache Camel 3.21.
- **Backend-for-Frontend (BFF)**: Exposes clean REST APIs to frontend applications while shielding internal system APIs, translating data formats, and orchestrating downstream services.
- **Modular Bundles**:
  - Custom OSGi bundles with hot-deployment (`chamadasonline`, `tasktoday`, `evasaoescolar`, `processadordearquivos`).
  - Declarative Blueprint contexts (`moodle-admin-camel-context.xml`, `keycloak-admin-camel-context.xml`).

### Pillar 4: BPM & Workflow Engine (Flowable)
- **Flowable UI 6.7.2**: Pinned specifically to version 6.7.2 to avoid engine regressions present in 6.8+.
- **Process Model**: Standard BPMN 2.0 XML definitions.
- **Human Tasks via Orbeon Forms**: Instead of crude, unstyled built-in forms, human tasks leverage Orbeon Forms XForms definitions for dynamic layouts, client/server validations, and document generation.
- **Process Orchestration**: Flowable processes coordinate automated service calls through Camel endpoints, keeping business logic clean and decoupled from low-level API details.

### Pillar 5: Applications & Ed-Tech Solutions
- **Administration**:
  - `fzlbpmsadmin`: Tauri v2 desktop app written in Rust and Angular.
  - `fzlbpmsadmin-web`: Pure Angular web SPA served statically or through Nginx.
- **Ed-Tech Ecosystem**:
  - **Moodle 5.x**: Customized LMS optimized for mathematics instruction, automated course/category provisioning, and student enrollment.
  - **Chamadas Online**: Classroom attendance verification system featuring multi-layered anti-fraud mechanisms (rotating TOTP codes, Haversine GPS geofencing, device fingerprinting, and stealth responses).
  - **Task Today**: Personal and academic task management PWA featuring offline support, category taxonomies, and Web Push notifications (VAPID/AES128GCM).
  - **Evasão Escolar**: Early-warning student dropout prevention monitoring system.

---

## 3. Communication & Integration Patterns

### 1. The BFF (Backend-For-Frontend) Pattern
Frontends do not speak directly to raw administrative APIs. For instance, `fzlbpmsadmin-web` does not invoke the Keycloak Admin REST API directly; instead, it sends requests to Camel REST endpoints on port 9090 (`/fzlbpms/admin/users`, `/fzlbpms/admin/organizations`). Camel verifies the caller's JWT, validates admin roles, and invokes Keycloak using its own confidential client credentials.

### 2. The Token-Forwarding Pattern
When a workflow is initiated by an administrator (such as creating an Organization in Keycloak via a Flowable BPMN process):
1. The user fills an Orbeon Form or triggers an API call with their Keycloak Bearer token.
2. The Camel bridge captures the incoming `Authorization: Bearer <token>`.
3. Camel starts the Flowable process instance, passing the token as a process variable (`callerAuthHeader`).
4. Flowable HTTP service tasks send `Authorization: ${callerAuthHeader}` when invoking downstream Camel administrative endpoints.
5. Camel verifies that the token carries the `admin` realm role on every step.

### 3. The Bridge & Decoupling Pattern
Flowable does not need to know the intricate parameter conventions of Moodle's REST API (`core_course_create_categories`, `core_course_create_courses`). Flowable calls a concise, clean Camel endpoint (`POST /fzlbpms/moodle/courses`). The Camel route maps the payload, injects Moodle's web service token (`wstoken`), issues the HTTP POST to Moodle's PHP REST server, and returns the result.

```
┌──────────────────┐               ┌──────────────────┐               ┌──────────────────┐
│  Flowable BPMN   │  HTTP POST    │   Apache Camel   │  REST Form-URLEnc│    Moodle LMS    │
│  Service Task    ├──────────────►│   Route (Jetty)  ├──────────────►│    PHP Server    │
│ (Clean JSON Body)│               │  (Injects Token) │               │(server.php?wstoken)
└──────────────────┘               └──────────────────┘               └──────────────────┘
```

---

## 4. Data Persistence & Storage Architecture

The platform separates data concerns across distinct database engines:

| Engine | Container | Database / Key Namespace | Purpose |
|---|---|---|---|
| **PostgreSQL 16** | `fzl-keycloak-db` | `keycloak` | Dedicated identity database for Keycloak realms, users, clients, and credentials. |
| **PostgreSQL 15** | `fzl-flowable-db` | `flowable` | Dedicated workflow database for Flowable process definitions, byte arrays, task instances, and history. |
| **PostgreSQL 16** | `fzl-postgresql` | `fzldb` | Main application database; hosts tables for `chamadasonline`, `tasktoday`, and future bundles. |
| | | `moodle` | Complete database schema for Moodle LMS. |
| | | `contatemeantes` | GPS device tracking data. |
| | | `orbeon` | Form definitions and submitted XML/JSON form instance data. |
| **MySQL 8.4** | `fzl-mysql` | `fzl_db` | Relational storage for legacy PHP applications and Yii2 frameworks. |
| **Redis 7** | `fzl-redis` | In-memory keys (AOF enabled) | High-performance caching and messaging queues. |
| **MongoDB 4.4** | `fzl-mongodb` | `overleaf` | Document storage configured for LaTeX / Overleaf collaborative editing. |
| **Elasticsearch** | `fzl-elasticsearch` | Indices (X-Pack secured) | Centralized search indexing and analytics visualization via Kibana. |
