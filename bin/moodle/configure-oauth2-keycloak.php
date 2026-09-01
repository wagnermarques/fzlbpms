<?php
/**
 * Idempotent, headless configuration of a Moodle OAuth2 issuer pointed at
 * the fzlbpms Keycloak realm, so Moodle's auth_oauth2 plugin can share the
 * same Keycloak SSO session as fzlbpmsadmin-web.
 *
 * Designed to run *inside* the fzl-php8.3-fpm image (as the one-shot
 * `moodle-oauth2-configurator` compose service), sharing the var_www/html
 * bind mount so it can see Moodle's config.php and classes. Safe to run on
 * every `docker compose up`: create-or-update + re-discover, never fatal.
 *
 * Required environment variables (set by docker-compose):
 *   KEYCLOAK_ISSUER_BASEURL     — e.g. https://fzlbpms.com.br/auth/realms/fzlbpms
 *   KEYCLOAK_MOODLE_CLIENT_ID   — Keycloak confidential client id ("moodle")
 *   KEYCLOAK_MOODLE_CLIENT_SECRET
 */

define('CLI_SCRIPT', true);

$moodleConfig = '/var/www/html/moodle/config.php';
if (!is_file($moodleConfig)) {
    fwrite(STDERR, "[oauth2-setup] Moodle not installed yet ({$moodleConfig} missing) — skipping.\n");
    exit(0);
}
require($moodleConfig);
require_once($CFG->libdir . '/clilib.php');

$name = 'Keycloak';
$baseurl = getenv('KEYCLOAK_ISSUER_BASEURL');
$clientid = getenv('KEYCLOAK_MOODLE_CLIENT_ID');
$clientsecret = getenv('KEYCLOAK_MOODLE_CLIENT_SECRET');

if (!$baseurl || !$clientid || !$clientsecret) {
    fwrite(STDERR, "[oauth2-setup] KEYCLOAK_ISSUER_BASEURL / KEYCLOAK_MOODLE_CLIENT_ID / "
        . "KEYCLOAK_MOODLE_CLIENT_SECRET must all be set.\n");
    exit(1);
}

try {
    // core\oauth2\api::* enforces moodle/site:config via require_capability(),
    // which needs a real (admin) $USER in session context for a CLI request.
    \core\session\manager::set_user(get_admin());

    // Moodle's SSRF protection (curl_security_helper) blocks curl requests to
    // private IP ranges by default — including 172.16.0.0/12, which is
    // exactly where Docker's default bridge network (and therefore
    // fzlbpms.local / fzl-nginx) lives. That's the right default for a
    // production site taking arbitrary user-supplied URLs, but here the
    // OAuth2 discovery target is our own docker-compose stack, not
    // user input, so it's safe to exempt this one range while leaving the
    // other defaults (loopback, 10.0.0.0/8, 192.168.0.0/16, cloud metadata
    // IP, etc.) blocked.
    $blockedhosts = array_filter(
        array_map('trim', explode("\n", $CFG->curlsecurityblockedhosts ?? '')),
        static fn($entry) => $entry !== '' && $entry !== '172.16.0.0/12'
    );
    set_config('curlsecurityblockedhosts', implode("\n", $blockedhosts));

    $data = (object) [
        'name' => $name,
        // The DB column is NOT NULL — an empty string means "no logo".
        'image' => '',
        'baseurl' => rtrim($baseurl, '/'),
        'clientid' => $clientid,
        'clientsecret' => $clientsecret,
        'loginscopes' => 'openid profile email',
        'loginscopesoffline' => 'openid profile email',
        'showonloginpage' => \core\oauth2\issuer::LOGINONLY,
        'requireconfirmation' => 0,
        // Left null so Moodle resolves the generic OIDC discovery service
        // and auto-fetches {baseurl}/.well-known/openid-configuration,
        // which also auto-maps given_name/family_name/email claims.
        'servicetype' => null,
    ];

    $existing = \core\oauth2\issuer::get_record(['name' => $name]);

    if ($existing) {
        $data->id = $existing->get('id');
        $issuer = \core\oauth2\api::update_issuer($data);
        echo "[oauth2-setup] Issuer '{$name}' updated.\n";
    } else {
        $issuer = \core\oauth2\api::create_issuer($data);
        echo "[oauth2-setup] Issuer '{$name}' created.\n";
    }

    // Neither create_issuer() nor update_issuer() runs OIDC discovery for a
    // custom issuer — without it there are no authorization/token/userinfo
    // endpoints and no claim mappings, and the login button silently fails.
    $found = \core\oauth2\service\custom::discover_endpoints($issuer);
    echo "[oauth2-setup] Discovery registered {$found} endpoint(s) from "
        . $data->baseurl . "/.well-known/openid-configuration\n";

    \core\oauth2\api::enable_issuer($issuer->get('id'));

    $enabled = get_enabled_auth_plugins();
    if (!in_array('oauth2', $enabled, true)) {
        set_config('auth', implode(',', array_unique(array_merge($enabled, ['oauth2']))));
        echo "[oauth2-setup] Enabled 'oauth2' auth plugin alongside: " . implode(',', $enabled) . "\n";
    } else {
        echo "[oauth2-setup] 'oauth2' auth plugin already enabled.\n";
    }

    // Promote Moodle's own dedicated SSO admin (FZL_MOODLE_ADMIN_USERNAME, the
    // Keycloak user Camel bootstraps — see keycloak-admin-camel-context.xml)
    // to Moodle site administrator. The Moodle account only exists after that
    // user's FIRST Keycloak login, and auth_oauth2 creates it with the email
    // as the username (e.g. 'moodle-admin@fzlbpms.local', not 'moodle-admin')
    // — so match oauth2 accounts by exact username OR by the local-part of
    // username/email.
    // Idempotent: appends to $CFG->siteadmins only when missing.
    $ssoadmin = getenv('MOODLE_SITEADMIN_USERNAME');
    if ($ssoadmin) {
        $match = null;
        foreach ($DB->get_records('user', ['auth' => 'oauth2', 'deleted' => 0]) as $u) {
            if ($u->username === $ssoadmin
                    || str_starts_with($u->username, $ssoadmin . '@')
                    || str_starts_with($u->email, $ssoadmin . '@')) {
                $match = $u;
                break;
            }
        }
        if ($match) {
            $admins = array_filter(array_map('trim', explode(',', $CFG->siteadmins ?? '')));
            if (!in_array((string) $match->id, array_map('strval', $admins), true)) {
                $admins[] = $match->id;
                set_config('siteadmins', implode(',', $admins));
                echo "[oauth2-setup] '{$match->username}' (id {$match->id}) promoted to site administrator.\n";
            } else {
                echo "[oauth2-setup] '{$match->username}' is already a site administrator.\n";
            }
        } else {
            echo "[oauth2-setup] SSO user '{$ssoadmin}' has no Moodle account yet (it is created "
                . "on their first Keycloak login) — re-run this configurator afterwards to promote them.\n";
        }
    }

    echo "[oauth2-setup] Done.\n";
} catch (\Throwable $e) {
    $detail = property_exists($e, 'debuginfo') && $e->debuginfo ? " — " . $e->debuginfo : '';
    fwrite(STDERR, "[oauth2-setup] ERROR — " . $e->getMessage() . $detail . "\n"
        . $e->getTraceAsString() . "\n");
    exit(1);
}
