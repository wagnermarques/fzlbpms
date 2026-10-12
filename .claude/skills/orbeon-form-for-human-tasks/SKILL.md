---
name: orbeon-form-for-human-tasks
description: Use an Orbeon Form (not Flowable's built-in form) for every human-facing input in a new Flowable process — a process start form or a mid-process userTask form. Use whenever authoring a new process that needs a human to fill something in, or adding a form to an existing one. Encodes the repo-specific traps (file layout, publish mechanism, button/process wiring precedence, the server-to-server send() security gap).
---

# Orbeon Form for human tasks in fzlbpms

Policy for this repo: **every new Flowable process that needs human input uses an Orbeon Form**, not `flowable:formProperty`. Flowable's own form support is weak (plain text fields, no layout, no validation beyond `required`); Orbeon gives real forms, server-side validation, and a UI the actual process operators already use. Pair this skill with `flowable-process-authoring` for the BPMN side — this skill covers the Orbeon side and the handoff between the two.

Reference implementation: `src-projects/orbeon-forms/create-organization/` (form) + `kc-provision-organization-from-form` in `keycloak-admin-camel-context.xml` (bridge) + `create-organization.bpmn20.xml` (process). Copy this trio's shape for a new process.

## File layout

- `src-projects/orbeon-forms/<form-dir>/form.xhtml` — the form itself.
- `src-projects/orbeon-forms/publish_forms.py` — add an entry to its `FORMS` list (`app`, `form`, `doc_id`, `title`, `description`) and re-run it (`python3 src-projects/orbeon-forms/publish_forms.py`) to push the XHTML into the Orbeon Postgres DB. Nothing is live until you do this — editing the file alone does nothing.
- `containers/fzl-orbeon/properties-local.xml.template` — the submit button + what it does. **Not** in `form.xhtml`.

By convention, app = `fzlbpms` for process-start forms, form name = the BPMN process key. (The existing `etec-coord` app is a document-generation feature, unrelated — don't mix the two.)

## `form.xhtml` is hand-writable — don't reach for Form Builder's UI

These files are plain, readable XForms/XHTML. Look at `src-projects/orbeon-forms/doc_13_2_despacho_diretor/form.xhtml` for the full shape; the skeleton for a simple one-section form:

```xml
<xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms"
         xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xi="http://www.w3.org/2001/XInclude"
         xmlns:xxi="http://orbeon.org/oxf/xml/xinclude" xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
         xmlns:fr="http://orbeon.org/oxf/xml/form-runner" xmlns:saxon="http://saxon.sf.net/">
    <xh:head>
        <xh:title>...</xh:title>
        <xf:model id="fr-form-model" xxf:expose-xpath-types="true">
            <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all">
                <form>
                    <mySection>
                        <fieldOne/>
                    </mySection>
                </form>
            </xf:instance>
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="mySection-bind" name="mySection" ref="mySection">
                    <xf:bind id="fieldOne-bind" name="fieldOne" ref="fieldOne" required="true()"/>
                </xf:bind>
            </xf:bind>
            <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
                <metadata>
                    <application-name>fzlbpms</application-name>
                    <form-name>my-process-key</form-name>
                    <title xml:lang="pt">...</title>
                    <description xml:lang="pt">...</description>
                    <version-comment>1.0.0</version-comment>
                </metadata>
            </xf:instance>
            <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all"><attachments/></xf:instance>
            <xf:instance id="fr-form-resources" xxf:readonly="false" xxf:exclude-result-prefixes="#all">
                <resources><resource xml:lang="pt">
                    <mySection><label>...</label></mySection>
                    <fieldOne><label>...</label><hint>...</hint></fieldOne>
                </resource></resources>
            </xf:instance>
        </xf:model>
    </xh:head>
    <xh:body>
        <fr:view><fr:body>
            <fr:section id="mySection-section" bind="mySection-bind">
                <xf:label ref="$form-resources/mySection/label"/>
                <fr:grid id="mySection-grid">
                    <xh:tr><xh:td>
                        <xf:input id="fieldOne-control" bind="fieldOne-bind">
                            <xf:label ref="$form-resources/fieldOne/label"/>
                            <xf:hint ref="$form-resources/fieldOne/hint"/>
                        </xf:input>
                    </xh:td></xh:tr>
                </fr:grid>
            </fr:section>
        </fr:body></fr:view>
    </xh:body>
</xh:html>
```

**Name every `xf:instance id="fr-form-instance"` element with a clean XML identifier — no hyphens.** `send(content="json")` serializes this exact data tree to JSON, verbatim. Clean names (`mySection.fieldOne`) let the Camel side read them with plain jsonpath dot notation (`$.mySection.fieldOne`); hyphenated names (as Form-Builder-generated forms like the old `create-moodle-user` draft used — `section-1`, `grid-1`) force ugly bracket notation (`$['section-1']['grid-1']['username']`) on the receiving end. Pick the clean names from the start.

## Wiring the submit button (`properties-local.xml.template`)

Two properties, scoped `<app>.<form>` (specific — NOT `<app>.*`, see below):

```xml
<property as="xs:string" name="oxf.fr.detail.buttons.fzlbpms.my-process-key" value="home summary my-action"/>

<property as="xs:string" name="oxf.fr.detail.process.my-action.fzlbpms.my-process-key">
    require-valid
    then send(
        uri = "http://fzl-karaf-camel-integration:9090/fzlbpms/admin/my-bridge-endpoint",
        content = "json",
        headers = "X-Orbeon-Shared-Secret: ${ORBEON_BRIDGE_SECRET}",
        replace = "all"
    )
</property>

<property as="xs:string" name="oxf.fr.resource.fzlbpms.my-process-key.pt.detail.buttons.my-action" value="Minha Ação"/>
```

**Use the specific form name, not the `app.*` wildcard**, unless you genuinely want every form under that app to share one button/action. Orbeon resolves `app.form` before `app.*`, so a form-specific property doesn't disturb siblings — but if you copy the existing `oxf.fr.detail.buttons.fzlbpms.*` (used by `create-moodle-user` today) for a NEW form, every other wildcard-matched form silently inherits that button too. Confirmed empirically when building the `create-organization` form alongside the existing `create-moodle-user` one.

`headers` syntax confirmed from Orbeon's own source (`form-runner/jvm/src/test/scala/.../SendTest.scala`, "must support multiple values for `headers`"): one string, `Name: Value`, multiple headers joined with a literal `\n`.

After editing the template, `fzl-orbeon` needs a rebuild+restart — `docker-entrypoint-fzl.sh` only renders it (via `envsubst`) at container start, it isn't a live mount.

## The trap: `send()` is server-to-server, with NO caller identity

`send()` fires from the **Orbeon Java process itself** (`fzl-orbeon` container → target, straight over the Docker network) — it never goes through the browser, nginx, or oauth2-proxy. The target endpoint gets **no bearer token, no `X-Forwarded-User`, nothing** identifying who clicked the button. Confirmed by reading the existing `create-user` wiring and curl-testing its target directly: that endpoint (and the whole `/fzlbpms/bpms/*` surface) is reachable by anyone with zero authentication today — a pre-existing gap, not something to repeat.

Two things every new Orbeon-triggered bridge route must do:

1. **Require a shared secret.** Check a fixed header (`X-Orbeon-Shared-Secret`) against `{{FZL_ORBEON_BRIDGE_SECRET}}` (env var, already wired into both `fzl-orbeon` and `fzl-karaf-camel-integration`) before doing anything else — `<choice><when><simple>${header.X-Orbeon-Shared-Secret} != '{{FZL_ORBEON_BRIDGE_SECRET}}'</simple>...403+stop...</when></choice>`. This closes direct-script access; it does NOT verify *which* human submitted the form — Orbeon's own `/orbeon/` login gate (today: the `theia-user` realm role, informally admin-only — see `containers/fzl-orbeon/README.md`) is the only thing standing in for that.
2. **If the process needs to call anything gated by `kc-authorize-admin`** (the `/fzlbpms/admin/*` user/role/organization CRUD API), there is no caller token to forward. Call `kc-get-fzlbpms-admin-token` instead (mints a real `fzlbpms`-realm token for `FZLBPMS_ADMIN_USERNAME`, who holds the `admin` role from bootstrap — no new secret needed, those env vars already exist) and pass IT as `callerAuthHeader`. The BPMN process itself needs no awareness of where the token came from — it already just does `Authorization: ${callerAuthHeader}` on every HTTP task, same as a browser-forwarded token.

## Checklist for a new process + form

1. Write `form.xhtml` (clean instance names), add it to `publish_forms.py`, run the script.
2. Add the form-specific button + process properties to `properties-local.xml.template`.
3. Add a bridge route in the appropriate Camel bundle: check the shared secret, extract fields via jsonpath (dot notation if you named things cleanly), mint `kc-get-fzlbpms-admin-token` if the process touches `/fzlbpms/admin/*`, reshape into Flowable's process-start JSON, `Basic admin:test` to `flowable-ui`.
4. Write the BPMN process per `flowable-process-authoring` — its HTTP tasks don't know or care that the trigger was Orbeon instead of a browser.
5. Rebuild+restart `fzl-orbeon` (template) and the Camel bundle's container (new routes). Publish the form. Test: curl the bridge with no secret header (expect 403), then fill the real form in the browser.
