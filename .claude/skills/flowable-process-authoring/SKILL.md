---
name: flowable-process-authoring
description: Author, deploy, and run a Flowable BPMN process in the fzlbpms stack. Use whenever creating or editing a *.bpmn20.xml process, adding HTTP/script/multi-instance tasks, deploying to the running flowable-ui, or debugging a process that fails to deploy or corrupts data. Encodes the repo-specific traps (charset, groovy availability, BPMN element order, REST auth).
---

# Authoring a Flowable process for fzlbpms

Runtime facts (verified 2026-07-28): flowable-ui image is **6.7.2 on Java 11** with **groovy-3.0.9** but **NOT groovy-json**. Processes live in `src-projects/flowable-processes/*.bpmn20.xml`. The reference working process is `moodle-provision-courses.bpmn20.xml`.

## Deploy + run (process REST API accepts basic auth `admin:test` even in oauth2 mode)

```bash
# deploy (201 on success)
curl -sk -u admin:test -F "file=@src-projects/flowable-processes/<name>.bpmn20.xml" \
  https://fzlbpms.local/flowable-ui/process-api/repository/deployments
# confirm the definition
curl -sk -u admin:test "https://fzlbpms.local/flowable-ui/process-api/repository/process-definitions?key=<processId>"
# start an instance
curl -sk -u admin:test -H "Content-Type: application/json" \
  -d '{"processDefinitionKey":"<processId>"}' \
  https://fzlbpms.local/flowable-ui/process-api/runtime/process-instances
```
Redeploying the same file creates a new version automatically. The response of a synchronous run includes all process variables — inspect them to see what each task produced/failed on. `-sk` because it's https://fzlbpms.local (host trusts the mkcert CA; use `-k` if not).

## Traps to avoid (each of these cost real debugging time)

1. **BPMN element order is schema-enforced.** In a `serviceTask`/`scriptTask`, `<extensionElements>` MUST come BEFORE `<multiInstanceLoopCharacteristics>`. Wrong order → deploy fails "Invalid content was found starting with element 'extensionElements'". Always validate first: `python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('<file>')"`.

2. **HTTP task encodes the request body as ISO-8859-1** — corrupts accents (é→�) and drops chars not in Latin-1 (en-dash → `?`). Setting `charset=UTF-8` in Content-Type does NOT fix it. FIX: URL-encode non-ASCII values in a Groovy script task (java.net.URLEncoder works on Java 11) and send pure-ASCII form-encoded values; have the downstream service urldecode. Never send raw accented UTF-8 through a Flowable HTTP task body.

3. **No groovy-json in script tasks.** `new groovy.json.JsonSlurper()` fails "unable to resolve class". Parse JSON with the Jackson ObjectMapper that IS on the classpath: `new com.fasterxml.jackson.databind.ObjectMapper().readTree(str)` → `.get(0).get('id').asInt()`. (JsonOutput/JsonSlurper are both unavailable.)

4. **Reading files from a script task**: mount the file into flowable-ui via docker-compose (see the `/opt/fzlbpms/csvs` mount) and read with `new File(path).readLines('UTF-8')`. Strip a leading BOM (`if (lines[0].startsWith('﻿')) ...`). The default groovy engine allows File I/O.

## Working patterns (copy from moodle-provision-courses.bpmn20.xml)

- **HTTP task**: `<serviceTask flowable:type="http">` with `<flowable:field name="requestMethod|requestUrl|requestHeaders|requestBody|responseVariableName|saveResponseParameters">`. `requestBody` uses `<flowable:expression>` for `${var}` substitution. Response body is stored as the string named by `responseVariableName`.
- **Call another container**: use its compose service name, e.g. `http://fzl-karaf-camel-integration:9091/...` (flowable-ui is on fzl-network).
- **Loop over a collection**: `<multiInstanceLoopCharacteristics isSequential="true" flowable:collection="${listVar}" flowable:elementVariable="item"/>`; reference `${item.field}` in the body. Build the collection as `ArrayList` of `HashMap` in a prior script task via `execution.setVariable(...)`.
- **Fail loudly**: `throw new org.flowable.common.engine.api.FlowableException("...")` in a script task when a prior step's response is unexpected.

Always include a minimal `<bpmndi:BPMNDiagram>` so the process renders in the Modeler.
