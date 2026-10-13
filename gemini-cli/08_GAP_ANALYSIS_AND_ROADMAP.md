# 08 — State Analysis, Gaps & Roadmap

> **Project**: `fzlbpms`  
> **Component**: Evolutionary Comparison, Technical Debt & Next Steps  
> **Last Updated**: October 12, 2026

---

## 1. Evolution: July 2026 vs. October 2026

A comparison between the initial project evaluation ([PROJECT_ANALYSIS.md](file:///home/wgn/fzlbpms/PROJECT_ANALYSIS.md), July 2026) and the current state (October 2026) demonstrates significant architectural maturation:

| Component / Goal | Status in July 2026 | Current Status (October 2026) | Assessment |
|---|---|---|---|
| **Identity / SSO (Keycloak)** | Running, but no realms or clients versioned. Described as *"deliberately postponed"*. | **Fully automated.** `keycloak-admin-camel-context.xml` bootstraps the `fzlbpms` realm, 7 clients, roles, and dedicated admin users on startup. | **Resolved** |
| **BPM Engine** | Transitioning from Camunda to Flowable 6.7.2. | Flowable fully stabilized with production BPMN processes (`create-organization`, `moodle-provision-courses`, `create-moodle-user`). | **Resolved** |
| **Human Task Forms** | Flowable default forms only. | **Orbeon Forms** fully integrated with custom XHTML forms, database persistence, and shared-secret Camel bridge. | **Resolved** |
| **Local HTTPS & SSL** | Manual script workarounds. | Fully automated via Ansible `local-https.yml` (`mkcert`), system trust, and automated container truststore injection. | **Resolved** |
| **Public Deployment** | Not configured. | **Cloudflare Zero Trust Tunnel** (`fzl-cloudflared`) integrated with automated switching via `switch-domain.sh`. | **Resolved** |
| **Developer IDE** | Standalone container idea. | **Eclipse Theia** web IDE running at `/theia/`, gated by `oauth2-proxy`, with custom `theia-plugin-bpmn-editor`. | **Resolved** |
| **Applications Ecosystem** | Moodle and Yii2 tests only. | Expanded to include **Chamadas Online** (with anti-fraud backend) and **Task Today** (with VAPID Web Push). | **Expanded** |

---

## 2. Identified Technical Gaps & Vulnerabilities

### 1. Evasão Escolar Backend
- **Status**: **Fully implemented & deployed (October 12, 2026).**
- **Capabilities**: PostgreSQL relational schema (`evasao_*` tables in `fzldb`), automatic risk calculation engine, dashboard statistics, student/class CRUD, attendance and grade logging with automatic risk escalation, and pedagogical interventions management. Gated by Keycloak RS256 JWT authentication and exposed via Nginx at `/api/evasaoescolar/`.

### 2. Moodle Mathematics Assessment Engine (STACK / Maxima)
- **Status**: While Moodle 5.x is operational and supports basic MathJax LaTeX rendering, computer-aided algebra assessment (STACK question type) requires a dedicated **Maxima CAS** backend container.
- **Impact**: Mathematics courses are currently limited to standard multiple-choice and numeric quiz questions.
- **Action Required**: Create a container for Maxima CAS and add an Ansible provisioning task to install the STACK Moodle plugin.

### 3. Database Co-location in `fzl-postgresql`
- **Status**: A single PostgreSQL instance (`fzl-postgresql`) hosts four distinct databases: `fzldb`, `moodle`, `contatemeantes`, and `orbeon`.
- **Impact**: Heavy load from Moodle student queries or Orbeon form rendering could impact the performance of Camel integration transactions.
- **Action Required**: Monitor database performance; introduce connection pooling (e.g., PgBouncer) or separate containers if concurrent load increases.

### 4. Documentation Drift in `README.org`
- **Status**: The root `README.org` still lists legacy startup instructions referencing direct container commands instead of `run-stack.sh basic` and `switch-domain.sh`.
- **Action Required**: Update `README.org` to reflect the current orchestration model.

---

## 3. Recommended Roadmap & Next Milestones

```
┌────────────────────────────────────────────────────────────────────────┐
│ MILESTONE 1: Complete Evasão Escolar Backend                          │
│ • Implement PostgreSQL schema in fzldb                                 │
│ • Implement student risk algorithms and Camel REST routes              │
│ • Wire Keycloak authentication roles                                   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────┴─────────────────────────────────────┐
│ MILESTONE 2: Ed-Tech Mathematics Expansion                             │
│ • Provision Maxima CAS container for STACK questions                   │
│ • Install STACK and GeoGebra question plugins in Moodle                │
│ • Author sample calculus and linear algebra question banks             │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────┴─────────────────────────────────────┐
│ MILESTONE 3: Observability & Platform Telemetry                        │
│ • Expose Prometheus metrics from Camel, Karaf, and Flowable UI         │
│ • Configure Grafana dashboards for workflow cycle times and attendance │
│ • Centralize log streams via Elasticsearch / Kibana pipeline           │
└────────────────────────────────────────────────────────────────────────┘
```
