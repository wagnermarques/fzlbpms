<?php
/**
 * Idempotent, headless enablement of Moodle's REST web services for the
 * fzlbpms Camel integration bundle (moodle-admin-camel-context.xml), which
 * creates course categories and courses from a Flowable process.
 *
 * Runs inside the fzl-php8.3-fpm image (one-shot `moodle-webservice-configurator`
 * compose service), sharing the var_www/html bind mount. Safe to run on every
 * `docker compose up`.
 *
 * What it ensures (each step is create-or-skip):
 *   1. Web services + the REST protocol are enabled.
 *   2. A dedicated service account `fzlbpms_ws` exists (auth=webservice), with
 *      the system Manager role — which carries moodle/category:manage and
 *      moodle/course:create, the capabilities the WS functions below require.
 *   3. An external service `fzlbpms_ws` exists and is enabled, exposing exactly
 *      the functions the bundle calls.
 *   4. A permanent token with the EXACT value of MOODLE_WS_TOKEN is bound to
 *      that user + service. Using a fixed value (from .env) — instead of
 *      Moodle's random token — is what lets the Camel bundle read the same
 *      token via {{MOODLE_WS_TOKEN}} without any runtime handoff.
 *
 * Required environment variables (set by docker-compose):
 *   MOODLE_WS_TOKEN   — 32-char hex token shared with the Camel bundle
 */

define('CLI_SCRIPT', true);

$moodleConfig = '/var/www/html/moodle/config.php';
if (!is_file($moodleConfig)) {
    fwrite(STDERR, "[ws-setup] Moodle not installed yet ({$moodleConfig} missing) — skipping.\n");
    exit(0);
}
require($moodleConfig);
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/accesslib.php');
require_once($CFG->dirroot . '/user/lib.php');
require_once($CFG->dirroot . '/webservice/lib.php');

$wstoken = getenv('MOODLE_WS_TOKEN');
if (!$wstoken || strlen($wstoken) < 16) {
    fwrite(STDERR, "[ws-setup] MOODLE_WS_TOKEN must be set (>=16 chars).\n");
    exit(1);
}

// The service exposes exactly what the fzlbpms bundle needs — nothing more.
$functions = [
    'core_course_create_categories',
    'core_course_get_categories',
    'core_course_create_courses',
    'core_course_get_courses_by_field',
];

try {
    global $DB, $CFG;

    // core APIs enforce capabilities via the session $USER; run as admin.
    \core\session\manager::set_user(get_admin());

    // --- 1. Enable web services + REST protocol -------------------------------
    if (empty($CFG->enablewebservices)) {
        set_config('enablewebservices', 1);
        echo "[ws-setup] Enabled web services.\n";
    }
    $protocols = array_filter(explode(',', $CFG->webserviceprotocols ?? ''));
    if (!in_array('rest', $protocols, true)) {
        $protocols[] = 'rest';
        set_config('webserviceprotocols', implode(',', $protocols));
        echo "[ws-setup] Enabled the REST web service protocol.\n";
    }

    // --- 2. Dedicated service account + Manager role --------------------------
    $wsusername = 'fzlbpms_ws';
    $wsuser = $DB->get_record('user', ['username' => $wsusername, 'deleted' => 0]);
    if (!$wsuser) {
        $new = new stdClass();
        $new->auth = 'webservice';   // token-based only; cannot log in interactively
        $new->username = $wsusername;
        $new->password = ''; // no interactive password; token auth doesn't use it
        $new->firstname = 'fzlbpms';
        $new->lastname = 'Web Service';
        $new->email = 'ws@fzlbpms.local';
        $new->confirmed = 1;
        $new->mnethostid = $CFG->mnet_localhost_id;
        $new->id = user_create_user($new, false, false);
        $wsuser = $DB->get_record('user', ['id' => $new->id]);
        echo "[ws-setup] Created service account '{$wsusername}' (id {$wsuser->id}).\n";
    } else {
        echo "[ws-setup] Service account '{$wsusername}' already exists (id {$wsuser->id}).\n";
    }

    $systemcontext = context_system::instance();
    $managerroleid = $DB->get_field('role', 'id', ['shortname' => 'manager'], MUST_EXIST);
    if (!user_has_role_assignment($wsuser->id, $managerroleid, $systemcontext->id)) {
        role_assign($managerroleid, $wsuser->id, $systemcontext->id);
        echo "[ws-setup] Assigned the system Manager role to '{$wsusername}'.\n";
    } else {
        echo "[ws-setup] '{$wsusername}' already has the system Manager role.\n";
    }

    // The Manager role carries the function capabilities (category:manage,
    // course:create) but NOT webservice/rest:use — Moodle requires that
    // per-protocol capability of the token user, else every call fails with
    // "Access control exception". Grant it on the Manager role (idempotent).
    $hasrestcap = $DB->record_exists('role_capabilities', [
        'roleid' => $managerroleid,
        'contextid' => $systemcontext->id,
        'capability' => 'webservice/rest:use',
        'permission' => CAP_ALLOW,
    ]);
    if (!$hasrestcap) {
        assign_capability('webservice/rest:use', CAP_ALLOW, $managerroleid, $systemcontext->id, true);
        $systemcontext->mark_dirty();
        echo "[ws-setup] Granted webservice/rest:use to the Manager role.\n";
    } else {
        echo "[ws-setup] Manager role already has webservice/rest:use.\n";
    }

    // --- 3. External service exposing the required functions ------------------
    $serviceshortname = 'fzlbpms_ws';
    $service = $DB->get_record('external_services', ['shortname' => $serviceshortname]);
    if (!$service) {
        $svc = new stdClass();
        $svc->name = 'fzlbpms Web Service';
        $svc->shortname = $serviceshortname;
        $svc->enabled = 1;
        // restrictedusers=0: any user holding a token for this service may call
        // it (capability checks still apply per function) — no per-user
        // authorisation row needed. Fine for an internal integration account.
        $svc->restrictedusers = 0;
        $svc->downloadfiles = 0;
        $svc->uploadfiles = 0;
        $svc->timecreated = time();
        $svc->id = $DB->insert_record('external_services', $svc);
        $service = $DB->get_record('external_services', ['id' => $svc->id]);
        echo "[ws-setup] Created external service '{$serviceshortname}' (id {$service->id}).\n";
    } else {
        if (!$service->enabled) {
            $DB->set_field('external_services', 'enabled', 1, ['id' => $service->id]);
            echo "[ws-setup] Re-enabled external service '{$serviceshortname}'.\n";
        }
        echo "[ws-setup] External service '{$serviceshortname}' already exists (id {$service->id}).\n";
    }

    foreach ($functions as $fname) {
        $exists = $DB->record_exists('external_services_functions', [
            'externalserviceid' => $service->id,
            'functionname' => $fname,
        ]);
        if (!$exists) {
            $DB->insert_record('external_services_functions', (object) [
                'externalserviceid' => $service->id,
                'functionname' => $fname,
            ]);
            echo "[ws-setup] Added function '{$fname}' to the service.\n";
        }
    }

    // --- 4. Permanent token with the exact MOODLE_WS_TOKEN value --------------
    // Bound to (user, service). A stale token with the same value on a
    // different user/service is replaced so re-runs converge.
    $existing = $DB->get_record('external_tokens', ['token' => $wstoken]);
    $needsinsert = true;
    if ($existing) {
        if ((int) $existing->userid === (int) $wsuser->id
                && (int) $existing->externalserviceid === (int) $service->id) {
            $needsinsert = false;
            echo "[ws-setup] Token already bound to '{$wsusername}' + '{$serviceshortname}'.\n";
        } else {
            $DB->delete_records('external_tokens', ['id' => $existing->id]);
            echo "[ws-setup] Removed a stale token record with the same value.\n";
        }
    }
    if ($needsinsert) {
        $token = new stdClass();
        $token->token = $wstoken;
        $token->tokentype = EXTERNAL_TOKEN_PERMANENT; // 0
        $token->userid = $wsuser->id;
        $token->externalserviceid = $service->id;
        $token->contextid = $systemcontext->id;
        $token->creatorid = get_admin()->id;
        $token->iprestriction = null;
        $token->validuntil = null;
        $token->timecreated = time();
        $token->lastaccess = null;
        // 'privatetoken' column exists in some versions; set when present.
        $columns = $DB->get_columns('external_tokens');
        if (isset($columns['privatetoken'])) {
            $token->privatetoken = null;
        }
        if (isset($columns['name'])) {
            $token->name = 'fzlbpms integration token';
        }
        $DB->insert_record('external_tokens', $token);
        echo "[ws-setup] Bound the MOODLE_WS_TOKEN value to '{$wsusername}' + '{$serviceshortname}'.\n";
    }

    echo "[ws-setup] Done.\n";
} catch (\Throwable $e) {
    $detail = property_exists($e, 'debuginfo') && $e->debuginfo ? " — " . $e->debuginfo : '';
    fwrite(STDERR, "[ws-setup] ERROR — " . $e->getMessage() . $detail . "\n"
        . $e->getTraceAsString() . "\n");
    exit(1);
}
