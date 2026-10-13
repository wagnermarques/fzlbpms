# 09 — Actionable Recommendations & Project Roadmap

> **Project**: `fzlbpms`  
> **Component**: Strategic & Technical Recommendations  
> **Date**: October 12, 2026

---

## Executive Overview

Based on our analysis of the codebase, git commit history, and active Docker containers, the **fzlbpms** platform has achieved strong architectural maturity in identity federation (Keycloak 26), integration routing (Camel 3.21 on Karaf), workflow automation (Flowable 6.7.2), and human task handling (Orbeon Forms).

To take the project to the next level of production readiness, scalability, and functional completeness, the following prioritized recommendations are proposed.

---

## Priority 1: High Impact / Immediate Value

### 1. Implement Business Logic in the Evasão Escolar Bundle
* **Current State**: The bundle `src-projects/karaf_bundles/blueprint-osgi-camel-bundles/evasaoescolar` is a skeleton with only a health-check endpoint (`/fzlbpms/evasaoescolar/health`).
* **Objective**: Replace the external Supabase dependency with internal Karaf/Camel/PostgreSQL services.
* **Concrete Steps**:
  1. Author `schema.sql` under `evasaoescolar/src/main/resources/sql/` creating tables in `fzldb` (e.g., student attendance logs, academic thresholds, alert flags, pedagogical interventions).
  2. Implement Camel routes and Java processors for:
     - Ingesting attendance data from Chamadas Online and Moodle.
     - Calculating dropout risk heuristics (e.g., consecutive absences, grade deterioration).
     - Managing intervention workflows.
  3. Secure endpoints with Keycloak JWT validation (following the pattern established in `tasktoday`).
  4. Create an Ansible playbook (`ansible/fzl-evasaoescolar-setup.yml`) for automated initialization.

### 2. Automated Smoke & Integration Test Suite
* **Current State**: Testing is currently conducted via manual curl commands or ad-hoc scripts.
* **Objective**: Create an automated smoke test suite (`bin/test-stack-smoke.sh` or Python/Playwright tests via `.playwright-mcp/`) that validates end-to-end functionality with a single command.
* **Coverage**:
  - Verification of all 20+ running container health endpoints.
  - Obtaining a Keycloak token via `/fzlbpms/auth/login`.
  - Testing Camel REST APIs (Keycloak admin BFF, Chamadas, Task Today, Evasão Escolar).
  - Deploying and executing a test Flowable BPMN workflow.
  - Verifying Orbeon forms rendering and submission bridge.
  - Validating Nginx reverse proxy routes under both HTTP and HTTPS.

---

## Priority 2: Ed-Tech & Educational Workflows

### 3. Containerize Maxima CAS for Moodle STACK Question Support
* **Current State**: Moodle 5.x is installed with MathJax support, but automated grading of complex algebraic mathematics requires the STACK question type engine.
* **Objective**: Enable full computer-algebra assessment for mathematics courses.
* **Concrete Steps**:
  1. Create a lightweight container (`containers/fzl-maxima` or STACK Maxima pool image) in `docker-compose.yml`.
  2. Mount Maxima socket/HTTP service into `fzl-php8.3-fpm` network.
  3. Author an Ansible provisioning task under `applications/moodle/` to automate the installation and configuration of the `qtype_stack` plugin and connection to the Maxima container.

### 4. End-to-End Orchestration: Enrollment to Course Creation
* **Current State**: `moodle-provision-courses.bpmn20.xml` provisions courses from CSV; `create-moodle-user.bpmn20.xml` provisions users.
* **Objective**: Unify these into a continuous administrative workflow:
  - Input: Academic semester curriculum definition via Orbeon Form.
  - Flowable process: Provisions Keycloak student groups, generates Moodle courses, registers classes in Chamadas Online, and configures Task Today native categories for the semester.

---

## Priority 3: Operations, Reliability & Security

### 5. Unified PostgreSQL Backup & Disaster Recovery Automation
* **Current State**: Specific scripts exist (e.g., `bin/moodle/moodledata-backup.sh`), but no unified, automated backup exists for the four relational databases running on `fzl-postgresql` (`fzldb`, `moodle`, `contatemeantes`, `orbeon`).
* **Concrete Steps**:
  1. Create `bin/backup-all-databases.sh` to execute consistent logical dumps (`pg_dump`) with timestamping, gzip compression, and retention cleanup (e.g., keep 7 daily, 4 weekly).
  2. Implement a companion restore verification script `bin/restore-database.sh`.
  3. Add an automated cron schedule configuration via Ansible.

### 6. Synchronize and Modernize Root Documentation (`README.org`)
* **Current State**: `README.org` still documents legacy manual startup commands (`docker compose up --build ...`) and does not reflect `./bin/run-stack.sh basic`, `./bin/switch-domain.sh`, or Ansible onboarding.
* **Concrete Steps**:
  - Update `README.org` with modern quickstart commands, profile descriptions, and links to the comprehensive documentation in `./gemini-cli/`.

### 7. Keycloak Production Mode Optimization
* **Current State**: `fzl-keycloak` runs in `start-dev` mode in `docker-compose.yml`.
* **Objective**: Prepare Keycloak for scalable production deployment by building an optimized Keycloak image (`kc.sh build`) and launching with `start --optimized`, tuning cache clusters and proxy header parsing.

---

## Suggested Next Work Item

Depending on your immediate goals, we can tackle any of the following tasks right now:

1. **Option A (Application Logic)**: Build out the PostgreSQL schema and core Camel REST routes for **Evasão Escolar**.
2. **Option B (Quality & CI/CD)**: Author an automated **Stack Smoke Test Suite** (`bin/test-stack-smoke.sh`) to verify all services and endpoints.
3. **Option C (Ed-Tech)**: Integrate **Maxima / STACK** container and provisioning for Moodle mathematics assessment.
4. **Option D (Operational Hardening)**: Create the **Unified Database Backup & Restore Automation** tool.
