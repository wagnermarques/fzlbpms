---
name: moodle-camel-integration
description: Add or debug a Moodle REST web-service integration in the fzlbpms Karaf/Camel bundle (create courses, categories, users, enrolments, etc.). Use when writing a Camel Blueprint route that calls Moodle web services, extending moodle-admin-camel-context.xml, provisioning Moodle web-service access, or debugging "Access control exception" / charset / groovy errors in Karaf.
---

# Calling Moodle web services from the Camel bundle

Reference bundle: `src-projects/karaf_bundles/blueprint-xmls-bundles/moodle-admin-camel-context.xml` (Jetty port **9091**; the keycloak bundle owns 9090). Karaf is **Camel 3.21 on Java 21**; boot features are `camel-blueprint, camel-http, camel-jetty, camel-jsonpath` (see `containers/fzl-karaf-camel-integration/custom_karaf_etc/etc-from-4.4.7/org.apache.karaf.features.cfg`).

## Moodle web-service setup (already provisioned; extend as needed)

`bin/moodle/configure-moodle-webservice.php` (one-shot `moodle-webservice-configurator` service, in the `basic` stack) enables REST, creates the `fzlbpms_ws` Manager account, and binds the fixed `MOODLE_WS_TOKEN` from .env. To expose a NEW function, add its name to the `$functions` array there and re-run `docker compose up moodle-webservice-configurator`.

- **"Access control exception" (accessexception)** = the token user lacks a capability. The Manager role covers most `moodle/...:...` caps but NOT the protocol cap `webservice/rest:use` — the script grants that to the Manager role. A new function may need another capability; check the function's `->capabilities` and grant on the Manager role.
- Look up any function's required params/capabilities in Moodle: `.../admin/webservice/documentation.php`, or its `externallib.php` `..._parameters()` definition.

## Calling Moodle from a Camel route

Endpoint env vars (set in docker-compose.yml for the karaf service): `MOODLE_WS_URL` (= `https://fzlbpms.local/moodle/webservice/rest/server.php`) and `MOODLE_WS_TOKEN`. The Karaf JVM trusts the mkcert CA via its entrypoint, so https to fzlbpms.local works.

Pattern for a route (form-urlencoded POST, JSON response):
```xml
<setProperty name="m.x"><jsonpath>$.x</jsonpath></setProperty>
<setBody>
  <simple>wstoken={{MOODLE_WS_TOKEN}}&amp;wsfunction=<FUNCTION>&amp;moodlewsrestformat=json&amp;<param>=${exchangeProperty.m.x}</simple>
</setBody>
<removeHeaders pattern="*"/>
<setHeader name="Content-Type"><constant>application/x-www-form-urlencoded</constant></setHeader>
<to uri="{{MOODLE_WS_URL}}?httpMethod=POST&amp;throwExceptionOnFailure=false"/>
<setHeader name="Content-Type"><constant>application/json</constant></setHeader>
```
Moodle WS array params use PHP bracket syntax, e.g. `courses[0][fullname]=...`, `categories[0][parent]=0`. Output format is JSON only (`moodlewsrestformat=json`); input is always form/query params (there is no JSON input).

## Hard constraints (learned the hard way)

1. **NO scripting language in Karaf routes.** camel-groovy 3.21 bundles Groovy 3.0.8, which crashes on Java 21 ("Unsupported class file major version 65"). Do not add camel-groovy. Use jsonpath + Camel Simple only. `java.net.URLEncoder` also can't be called via the Camel bean component (private constructor).

2. **ENCODING CONTRACT**: this bundle forwards string values verbatim into the Moodle form body — **callers must pre-URL-encode** non-ASCII values (the Flowable process does this in a Groovy task). Do NOT try to URL-encode inside the Camel route. A plain UTF-8 value also round-trips for names without `% & + = #` (PHP urldecode passes raw bytes), which is why simple curl tests work.

3. **Changing featuresBoot needs a cache wipe.** Editing `org.apache.karaf.features.cfg` only takes effect after `docker compose build fzl-karaf-camel-integration`, stopping it, wiping `containers/fzl-karaf-camel-integration/karaf-data/cache` (root-owned — use `docker run --rm -v .../karaf-data:/data alpine rm -rf /data/cache`), then restarting.

## Fast iteration + testing

- Hot-deploy a bundle edit without rebuild: `docker cp <bundle>.xml fzl-karaf-camel-integration:/opt/karaf/deploy/` (Felix fileinstall reloads it). The deploy dir is container-owned, so `docker cp` not host `cp`.
- Watch errors: `docker exec fzl-karaf-camel-integration bash -c "tail -n 80 /opt/karaf/data/log/karaf.log"`.
- Test an endpoint directly: `curl -s -X POST http://localhost:9091/fzlbpms/moodle/<path> -H 'Content-Type: application/json' -d '{...}'`.
- Validate XML before deploying: `python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('<file>')"`. Remember literal `&` inside a `<simple>` expression must be written `&amp;`.
