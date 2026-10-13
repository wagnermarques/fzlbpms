# 04 — BPMN Workflows & Orbeon Forms

> **Project**: `fzlbpms`  
> **Component**: Flowable 6.7.2 BPM Engine, BPMN Authoring & Orbeon Forms  
> **Last Updated**: October 12, 2026

---

## 1. Workflow Architecture Overview

The business process orchestration in **fzlbpms** is powered by **Flowable UI 6.7.2**, backed by a dedicated PostgreSQL 15 database (`fzl-flowable-db` on port 5435).

A key architectural design principle in this platform is the separation of concerns:
- **Flowable Engine**: Manages process lifecycles, states, timers, sequential loops, and asynchronous task execution.
- **Apache Camel**: Executes system-level integrations, protocol mediation, and API transformations.
- **Orbeon Forms**: Handles all human-facing input forms (process start forms and mid-process user tasks).

---

## 2. The Flowable Engine Environment

### Version Pinning Rationale
The stack explicitly uses `flowable/flowable-ui:6.7.2`. Testing during platform development revealed that Flowable 6.8.0 introduced a breaking regression in `DefaultAsyncTaskExecutor`, causing `NullPointerException` errors during asynchronous task executions. Version 6.7.2 running on Java 11 is stable across all workload types.

### Deployment & Process REST API
Processes are authored as declarative BPMN 2.0 XML files (`src-projects/flowable-processes/*.bpmn20.xml`) and deployed dynamically to Flowable via HTTP:

```bash
# Deploy process definition
curl -sk -u admin:test \
  -F "file=@src-projects/flowable-processes/create-organization.bpmn20.xml" \
  https://fzlbpms.local/flowable-ui/process-api/repository/deployments

# Start a process instance programmatically
curl -sk -u admin:test \
  -H "Content-Type: application/json" \
  -d '{"processDefinitionKey":"create-organization", "variables":[{"name":"orgName","value":"Acme Corp"}]}' \
  https://fzlbpms.local/flowable-ui/process-api/runtime/process-instances
```

---

## 3. Critical BPMN Authoring Rules & Traps

The codebase documents several hard-won lessons regarding Flowable's internal behavior:

### Trap 1: Schema-Enforced XML Element Order
In BPMN `<serviceTask>` or `<scriptTask>` elements, `<extensionElements>` **MUST** appear BEFORE `<multiInstanceLoopCharacteristics>`. Reversing the order causes Flowable to reject deployment with:
> `Invalid content was found starting with element 'extensionElements'`

Always validate with Python before deploying:
```bash
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('src-projects/flowable-processes/my-process.bpmn20.xml')"
```

### Trap 2: Flowable HTTP Task Encoding Defect (ISO-8859-1)
Flowable's built-in HTTP task (`flowable:type="http"`) forcibly re-encodes request bodies as **ISO-8859-1**, regardless of declared `Content-Type: application/json; charset=UTF-8` headers. This corrupts Portuguese accents (`é` → ``) and replaces characters outside Latin-1 with question marks (`?`).
- **Solution**: Pre-convert UTF-8 bytes to ISO-8859-1 chars within a Groovy script task so that Flowable's re-encoding reconstructs the true UTF-8 bytes on the wire, or pre-URL-encode all string variables via `java.net.URLEncoder.encode(val, "UTF-8")` and have downstream Camel routes decode them.

### Trap 3: No `groovy-json` on Classpath
Flowable 6.7.2 bundles Groovy 3.0.9, but excludes `groovy-json`. Calling `new groovy.json.JsonSlurper()` fails with `ClassNotFoundException`.
- **Solution**: Use Jackson's `ObjectMapper`, which is available on the classpath:
  ```groovy
  def mapper = new com.fasterxml.jackson.databind.ObjectMapper()
  def jsonNode = mapper.readTree(rawResponseString)
  def recordId = jsonNode.get("id").asInt()
  ```

### Trap 4: Variable Interpolation in Headers (`flowable:expression`)
In HTTP task fields, `<flowable:string>` treats the content as raw text and **does NOT** evaluate expressions (`${...}`).
- **Solution**: Always use `<flowable:expression>`:
  ```xml
  <flowable:field name="requestHeaders">
    <flowable:expression>Content-Type: application/json
  Authorization: ${callerAuthHeader}</flowable:expression>
  </flowable:field>
  ```

---

## 4. Deployed BPMN Workflows

### 1. `create-organization.bpmn20.xml`
- **Objective**: Automates the creation of a Keycloak 26 Organization along with default owner (`org-<alias>-dono`) and member (`org-<alias>-membro`) roles.
- **Security**: Propagates the administrator's JWT (`callerAuthHeader`) end-to-end to ensure administrative privileges are validated at every step.
- **Tasks**:
  1. `prepareVariables`: Slugifies alias, generates role names, wire-safe encodes the name.
  2. `createOrganization`: HTTP POST to Camel port 9090 (`/fzlbpms/admin/organizations`).
  3. `createRoleDono` & `createRoleMembro`: Creates associated Keycloak roles.
  4. `buildSummary`: Returns execution confirmation.

### 2. `moodle-provision-courses.bpmn20.xml`
- **Objective**: Automates bulk provisioning of Moodle course categories and course shells from a structured CSV source.
- **Tasks**:
  1. Reads CSV file mounted at `/opt/fzlbpms/csvs`.
  2. Parses categories, strips BOM, URL-encodes accents.
  3. Executes sequential multi-instance loop calling Camel port 9091 (`/fzlbpms/moodle/categories`).
  4. Creates courses within generated categories (`/fzlbpms/moodle/courses`).

### 3. `create-moodle-user.bpmn20.xml`
- **Objective**: Provisions a user account inside Moodle bound exclusively to Keycloak OAuth2 Single Sign-On.

---

## 5. Orbeon Forms for Human Tasks

### Policy & Rationale
Flowable's built-in forms (`flowable:formProperty`) offer limited styling and lack dynamic validation. **fzlbpms mandates Orbeon Forms for all human interactions**.

### Form Definitions & Structure
Forms reside in `src-projects/orbeon-forms/<form-name>/form.xhtml` using standard W3C XForms / XHTML syntax:
- `create-organization`: Form for registering new organizational tenants.
- `doc_13_processo_aproveitamento_unificado`: Academic course equivalency / credit transfer request.
- `doc_13_1_parecer_comissao`: Committee evaluation report.
- `doc_13_2_despacho_diretor`: Directorate decision and signature.

### Form Publishing Pipeline
Forms are not edited through Form Builder UI directly in production; they are managed as code and published to PostgreSQL via `publish_forms.py`:
```bash
python3 src-projects/orbeon-forms/publish_forms.py
```
This script populates Orbeon's relational tables (`orbeon_form_data`, `orbeon_form_definition`) inside `fzl-postgresql`.

### Server-to-Server Bridge Security
When a user submits an Orbeon form:
1. Orbeon's process pipeline executes `send(uri="http://fzl-karaf-camel-integration:9090/...", content="json")`.
2. Because this is a server-to-server call without a user session cookie, Orbeon attaches a fixed secret header: `X-Orbeon-Shared-Secret: ${ORBEON_BRIDGE_SECRET}`.
3. Camel validates this secret header before launching or advancing the corresponding Flowable process.

---

## 6. BPMN Modeling Tools

1. **`fzl-bpmn-drawer`**: Web-based modeler based on bpmn-js, accessible at `https://fzlbpms.local/bpmndrawer/` (or port 8085).
2. **Eclipse Theia BPMN Editor Plugin**: A custom-developed VSIX extension (`src-projects/fzl-theia-plugins/theia-plugin-bpmn-editor`) allowing developers to open and edit `.bpmn` and `.bpmn20.xml` diagrams directly inside the web IDE.
