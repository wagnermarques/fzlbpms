#!/usr/bin/env bash
# =============================================================================
# install-moodle-in-container.sh
#
# Idempotent, headless Moodle installer. Designed to run *inside* the
# fzl-php8.3-fpm image (as the one-shot `moodle-installer` compose service),
# sharing the var_www/html and moodledata bind mounts and the fzl-network so
# it can reach the `fzl-postgresql` database by service name.
#
# It is safe to run on every `docker compose up`: each step is guarded, so a
# second run is a no-op that exits 0.
#
# Steps:
#   1. Create the Moodle Postgres role + database (as superuser, via PHP PDO).
#   2. Download + extract the pinned stable Moodle tarball (if absent).
#   3. Fix ownership (files must be owned by www-data for the web server).
#   4. Run Moodle's CLI installer (writes config.php + schema + admin user).
#   5. Purge caches.
#
# All configuration comes from environment variables (see docker-compose.yml
# / .env). No host `docker exec` and no `pkexec` — this replaces the desktop
# app's install flow for automated stack startup.
# =============================================================================
set -euo pipefail

# --- Configuration (from environment) ---------------------------------------
MOODLE_VERSION="${MOODLE_VERSION:?MOODLE_VERSION is required}"
PG_HOST="${MOODLE_DB_HOST:-fzl-postgresql}"
PG_PORT="${MOODLE_DB_PORT:-5432}"
PG_SUPERUSER="${FZL_POSTGRES_USER:?FZL_POSTGRES_USER is required}"
PG_SUPERPASS="${FZL_POSTGRES_PASSWORD:?FZL_POSTGRES_PASSWORD is required}"

MOODLE_DB_NAME="${MOODLE_DB_NAME:?MOODLE_DB_NAME is required}"
MOODLE_DB_USER="${MOODLE_DB_USER:?MOODLE_DB_USER is required}"
MOODLE_DB_PASS="${MOODLE_DB_PASS:?MOODLE_DB_PASS is required}"
MOODLE_DB_PREFIX="${MOODLE_DB_PREFIX:-mdl_}"

MOODLE_WWWROOT="${MOODLE_WWWROOT:?MOODLE_WWWROOT is required}"
MOODLE_ADMIN_USER="${MOODLE_ADMIN_USER:?MOODLE_ADMIN_USER is required}"
MOODLE_ADMIN_PASS="${MOODLE_ADMIN_PASS:?MOODLE_ADMIN_PASS is required}"
MOODLE_ADMIN_EMAIL="${MOODLE_ADMIN_EMAIL:?MOODLE_ADMIN_EMAIL is required}"
MOODLE_FULLNAME="${MOODLE_FULLNAME:-fzlbpms Moodle}"
MOODLE_SHORTNAME="${MOODLE_SHORTNAME:-fzlbpms}"

MOODLE_DIR="${MOODLE_DIR:-/var/www/html/moodle}"
MOODLEDATA_DIR="${MOODLEDATA_DIR:-/moodledata}"
WEB_USER="${WEB_USER:-www-data}"

# --- Extra plugins from a GitHub Releases page ------------------------------
# Bundle third-party Moodle plugins straight from a GitHub repo's release
# assets (one .zip per plugin). Each zip's version.php `$plugin->component`
# decides which Moodle directory it goes into, so this is generic across plugin
# types. Set MOODLE_PLUGINS_ENABLED=0 to skip entirely.
MOODLE_PLUGINS_ENABLED="${MOODLE_PLUGINS_ENABLED:-1}"
MOODLE_PLUGINS_REPO="${MOODLE_PLUGINS_REPO:-wagnermarques/fzlbpms-moodle-plugins}"
MOODLE_PLUGINS_RELEASE="${MOODLE_PLUGINS_RELEASE:-latest}"   # 'latest' or a tag
PLUGINS_CHANGED=0                                            # set by install_plugins

log() { echo "[moodle-installer] $*"; }

# Derive the download.moodle.org "stable" directory from the version.
#   5.2.1 -> stable502   |   4.5.3 -> stable405
STABLE_DIR="stable$(awk -F. '{ printf "%d%02d", $1, $2 }' <<<"$MOODLE_VERSION")"
TARBALL_URL="https://download.moodle.org/download.php/direct/${STABLE_DIR}/moodle-${MOODLE_VERSION}.tgz"

# -----------------------------------------------------------------------------
# Step 1: create the Postgres role + database (idempotent, as superuser).
# psql is not in the image, so we use PHP's pdo_pgsql. CREATE ROLE/DATABASE
# cannot run inside a transaction, so each statement is issued on its own.
# -----------------------------------------------------------------------------
create_database() {
    log "Ensuring Postgres role '${MOODLE_DB_USER}' and database '${MOODLE_DB_NAME}' exist on ${PG_HOST}:${PG_PORT}..."
    PGHOST="$PG_HOST" PGPORT="$PG_PORT" PGSUPERUSER="$PG_SUPERUSER" \
    PGSUPERPASS="$PG_SUPERPASS" DBNAME="$MOODLE_DB_NAME" DBUSER="$MOODLE_DB_USER" \
    DBPASS="$MOODLE_DB_PASS" php -r '
        $dsn = sprintf("pgsql:host=%s;port=%s;dbname=postgres",
            getenv("PGHOST"), getenv("PGPORT"));
        try {
            $pdo = new PDO($dsn, getenv("PGSUPERUSER"), getenv("PGSUPERPASS"),
                [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
        } catch (Exception $e) {
            fwrite(STDERR, "DB connection failed: " . $e->getMessage() . "\n");
            exit(1);
        }
        $user = $pdo->quote(getenv("DBUSER"));
        $pass = $pdo->quote(getenv("DBPASS"));
        $db   = getenv("DBNAME");
        $dbq  = "\"" . str_replace("\"", "\"\"", $db) . "\"";      // identifier
        $userIdent = "\"" . str_replace("\"", "\"\"", getenv("DBUSER")) . "\"";

        // Role
        $exists = $pdo->query("SELECT 1 FROM pg_roles WHERE rolname = $user")->fetchColumn();
        if ($exists) {
            echo "  role already exists\n";
            $pdo->exec("ALTER ROLE $userIdent WITH LOGIN PASSWORD $pass");
        } else {
            $pdo->exec("CREATE ROLE $userIdent WITH LOGIN PASSWORD $pass");
            echo "  role created\n";
        }

        // Database
        $exists = $pdo->query("SELECT 1 FROM pg_database WHERE datname = " . $pdo->quote($db))->fetchColumn();
        if ($exists) {
            echo "  database already exists\n";
        } else {
            $pdo->exec("CREATE DATABASE $dbq OWNER $userIdent ENCODING \"UTF8\"");
            echo "  database created\n";
        }
        $pdo->exec("GRANT ALL PRIVILEGES ON DATABASE $dbq TO $userIdent");
    '
}

# -----------------------------------------------------------------------------
# Step 2: download + extract the stable tarball (only if not already present).
# -----------------------------------------------------------------------------
download_moodle() {
    # Moodle 5.0+ keeps version.php under public/; earlier releases at dirroot.
    if [ -f "${MOODLE_DIR}/version.php" ] || [ -f "${MOODLE_DIR}/public/version.php" ]; then
        log "Moodle source already present at ${MOODLE_DIR} (skipping download)."
        return
    fi
    log "Downloading Moodle ${MOODLE_VERSION} from ${TARBALL_URL} ..."
    local tmp
    tmp="$(mktemp)"
    curl -fSL --retry 3 -o "$tmp" "$TARBALL_URL"
    log "Extracting into $(dirname "$MOODLE_DIR") ..."
    # The tarball's top-level directory is 'moodle', so it lands at MOODLE_DIR.
    mkdir -p "$(dirname "$MOODLE_DIR")"
    tar -xzf "$tmp" -C "$(dirname "$MOODLE_DIR")"
    rm -f "$tmp"
    log "Moodle source extracted."
}

# -----------------------------------------------------------------------------
# Step 2b: bundle extra plugins from GitHub Releases (before install/upgrade so
# their schema is created by Moodle itself). Idempotent and version-aware:
# a plugin is (re)extracted only when missing or when the release ships a newer
# $plugin->version than what's on disk.
# -----------------------------------------------------------------------------

# Moodle 5.0+ serves from public/; earlier versions from the dirroot. Plugin
# directories (blocks/, mod/, ...) live under whichever is the code root.
moodle_code_root() {
    if [ -d "${MOODLE_DIR}/public" ]; then echo "${MOODLE_DIR}/public"; else echo "${MOODLE_DIR}"; fi
}

# Map a plugin component (e.g. block_user_category_courses) to its Moodle
# subdirectory, relative to the code root. Empty = unknown type (skip).
plugin_subdir_for_component() {
    case "${1%%_*}" in
        mod)          echo "mod" ;;
        block)        echo "blocks" ;;
        local)        echo "local" ;;
        theme)        echo "theme" ;;
        auth)         echo "auth" ;;
        enrol)        echo "enrol" ;;
        filter)       echo "filter" ;;
        format)       echo "course/format" ;;
        report)       echo "report" ;;
        coursereport) echo "course/report" ;;
        tool)         echo "admin/tool" ;;
        qtype)        echo "question/type" ;;
        qbehaviour)   echo "question/behaviour" ;;
        qformat)      echo "question/format" ;;
        repository)   echo "repository" ;;
        portfolio)    echo "portfolio" ;;
        availability) echo "availability/condition" ;;
        customfield)  echo "customfield/field" ;;
        profilefield) echo "user/profile/field" ;;
        datafield)    echo "mod/data/field" ;;
        datapreset)   echo "mod/data/preset" ;;
        webservice)   echo "webservice" ;;
        editor)       echo "lib/editor" ;;
        antivirus)    echo "lib/antivirus" ;;
        media)        echo "media/player" ;;
        cachestore)   echo "cache/stores" ;;
        cachelock)    echo "cache/locks" ;;
        gradereport)  echo "grade/report" ;;
        gradeexport)  echo "grade/export" ;;
        gradingform)  echo "grade/grading/form" ;;
        dataformat)   echo "dataformat" ;;
        message)      echo "message/output" ;;
        *)            echo "" ;;
    esac
}

# Pull a key's integer/string value out of a plugin version.php stream on stdin.
# $1 = key (component|version); prints the first match's value.
version_php_value() {
    case "$1" in
        component) grep -oE "component[[:space:]]*=[[:space:]]*'[^']+'" | head -1 | sed -E "s/.*'([^']+)'.*/\1/" ;;
        version)   grep -oE "version[[:space:]]*=[[:space:]]*[0-9]+"    | head -1 | grep -oE '[0-9]+' ;;
    esac
}

install_plugins() {
    [ "$MOODLE_PLUGINS_ENABLED" = "1" ] || { log "Plugin bundling disabled (MOODLE_PLUGINS_ENABLED=0)."; return 0; }

    local code_root; code_root="$(moodle_code_root)"
    local api_url
    if [ "$MOODLE_PLUGINS_RELEASE" = "latest" ]; then
        api_url="https://api.github.com/repos/${MOODLE_PLUGINS_REPO}/releases/latest"
    else
        api_url="https://api.github.com/repos/${MOODLE_PLUGINS_REPO}/releases/tags/${MOODLE_PLUGINS_RELEASE}"
    fi

    log "Fetching plugin release '${MOODLE_PLUGINS_RELEASE}' from ${MOODLE_PLUGINS_REPO} ..."
    # GitHub's API needs a User-Agent; PHP parses the JSON robustly (no jq).
    local urls
    urls="$(API_URL="$api_url" php -r '
        $ctx = stream_context_create(["http" => [
            "header"  => "User-Agent: fzlbpms-moodle-installer\r\nAccept: application/vnd.github+json\r\n",
            "timeout" => 30,
        ]]);
        $json = @file_get_contents(getenv("API_URL"), false, $ctx);
        if ($json === false) { fwrite(STDERR, "GitHub API request failed\n"); exit(1); }
        $data = json_decode($json, true);
        if (!isset($data["assets"])) { fwrite(STDERR, "no assets in release\n"); exit(1); }
        foreach ($data["assets"] as $a) {
            if (str_ends_with(strtolower($a["name"]), ".zip")) echo $a["browser_download_url"], "\n";
        }
    ')" || { log "WARNING: could not fetch plugin release — skipping plugin bundling."; return 0; }

    if [ -z "$urls" ]; then log "No .zip plugin assets in release; nothing to bundle."; return 0; fi

    local tmpd; tmpd="$(mktemp -d)"
    # here-string (not a pipe) so PLUGINS_CHANGED persists in this shell.
    while IFS= read -r url; do
        [ -n "$url" ] || continue
        local zip="${tmpd}/$(basename "$url")"
        log "Downloading $(basename "$url") ..."
        if ! curl -fSL --retry 3 -o "$zip" "$url"; then
            log "WARNING: download failed for $(basename "$url"); skipping."; continue
        fi

        local component; component="$(unzip -p "$zip" '*/version.php' 2>/dev/null | version_php_value component)"
        if [ -z "$component" ]; then log "WARNING: no \$plugin->component in $(basename "$zip"); skipping."; continue; fi

        local subdir; subdir="$(plugin_subdir_for_component "$component")"
        if [ -z "$subdir" ]; then log "WARNING: unknown plugin type for '${component}'; skipping."; continue; fi

        local name="${component#*_}"
        local dest="${code_root}/${subdir}/${name}"
        local new_ver; new_ver="$(unzip -p "$zip" '*/version.php' 2>/dev/null | version_php_value version)"

        if [ -f "${dest}/version.php" ]; then
            local cur_ver; cur_ver="$(version_php_value version < "${dest}/version.php")"
            if [ -n "$new_ver" ] && [ -n "$cur_ver" ] && [ "$new_ver" -le "$cur_ver" ]; then
                log "Plugin ${component} up to date (on disk ${cur_ver} >= release ${new_ver}); skipping."
                continue
            fi
            log "Updating plugin ${component} (${cur_ver:-?} -> ${new_ver:-?}) ..."
            rm -rf "$dest"
        else
            log "Installing plugin ${component} -> ${subdir}/${name} ..."
        fi

        # The zip's top folder may not match the required dir name, so extract to
        # a scratch dir and move its single top-level folder into place.
        mkdir -p "${code_root}/${subdir}"
        local xdir="${tmpd}/x_${name}"; rm -rf "$xdir"; mkdir -p "$xdir"
        unzip -q "$zip" -d "$xdir"
        local top; top="$(find "$xdir" -mindepth 1 -maxdepth 1 -type d | head -1)"
        if [ -z "$top" ]; then log "WARNING: empty archive for ${component}; skipping."; continue; fi
        mv "$top" "$dest"
        PLUGINS_CHANGED=1
    done <<< "$urls"

    rm -rf "$tmpd"
}

# -----------------------------------------------------------------------------
# Step 3: ownership. Web server (www-data) must own the code + data dirs.
# -----------------------------------------------------------------------------
fix_permissions() {
    log "Setting ownership to ${WEB_USER} ..."
    mkdir -p "$MOODLEDATA_DIR"
    chown -R "${WEB_USER}:${WEB_USER}" "$MOODLE_DIR" "$MOODLEDATA_DIR"
    chmod -R 755 "$MOODLE_DIR"
    chmod 775 "$MOODLEDATA_DIR"
}

# Locate the CLI dir: Moodle 5.0+ moved admin/ under public/.
cli_path() {
    if [ -f "${MOODLE_DIR}/public/admin/cli/install.php" ]; then
        echo "public/admin/cli"
    else
        echo "admin/cli"
    fi
}

# -----------------------------------------------------------------------------
# Step 4: run the CLI installer (writes config.php + DB schema + admin user).
# Guarded on config.php so re-runs are a no-op.
# -----------------------------------------------------------------------------
install_moodle() {
    # Only the real config.php at the dirroot means "installed". Moodle 5.x
    # ships public/config.php as a shim (it just requires ../config.php), so it
    # must NOT be treated as evidence of a completed install.
    if [ -f "${MOODLE_DIR}/config.php" ]; then
        log "config.php already exists — Moodle is already installed (skipping)."
        return
    fi
    local cli; cli="$(cli_path)"
    log "Running Moodle CLI installer (${cli}/install.php) ..."
    # Run as the web user so config.php + dataroot are owned correctly.
    su -s /bin/bash -c "cd '${MOODLE_DIR}' && php ${cli}/install.php \
        --non-interactive \
        --agree-license \
        --lang=en \
        --wwwroot='${MOODLE_WWWROOT}' \
        --dataroot='${MOODLEDATA_DIR}' \
        --dbtype=pgsql \
        --dbhost='${PG_HOST}' \
        --dbport='${PG_PORT}' \
        --dbname='${MOODLE_DB_NAME}' \
        --dbuser='${MOODLE_DB_USER}' \
        --dbpass='${MOODLE_DB_PASS}' \
        --prefix='${MOODLE_DB_PREFIX}' \
        --fullname='${MOODLE_FULLNAME}' \
        --shortname='${MOODLE_SHORTNAME}' \
        --adminuser='${MOODLE_ADMIN_USER}' \
        --adminpass='${MOODLE_ADMIN_PASS}' \
        --adminemail='${MOODLE_ADMIN_EMAIL}'" "$WEB_USER"
    log "Moodle installed."

    # Moodle's CLI installer has no --sslproxy flag. TLS terminates at
    # Cloudflare/nginx — PHP only ever sees plain http — so without this,
    # Moodle doesn't trust X-Forwarded-Proto, treats every request as
    # insecure, and loops forever trying to "fix" it via redirect().
    if ! grep -q 'sslproxy' "${MOODLE_DIR}/config.php"; then
        sed -i "/require_once/i \$CFG->sslproxy = true;" "${MOODLE_DIR}/config.php"
        log "Patched config.php with \$CFG->sslproxy = true;"
    fi
}

# On an ALREADY-installed Moodle, newly bundled/updated plugins need an explicit
# upgrade to register their schema. (A fresh install gets this for free via
# install.php, so this only runs when config.php pre-existed.)
upgrade_plugins() {
    local cli; cli="$(cli_path)"
    log "Registering new/updated plugins via ${cli}/upgrade.php ..."
    su -s /bin/bash -c "cd '${MOODLE_DIR}' && php ${cli}/upgrade.php --non-interactive" "$WEB_USER"
}

purge_caches() {
    local cli; cli="$(cli_path)"
    if [ -f "${MOODLE_DIR}/${cli}/purge_caches.php" ]; then
        su -s /bin/bash -c "cd '${MOODLE_DIR}' && php ${cli}/purge_caches.php" "$WEB_USER" || true
    fi
}

database_is_initialized() {
    PGHOST="$PG_HOST" PGPORT="$PG_PORT" DBNAME="$MOODLE_DB_NAME" DBUSER="$MOODLE_DB_USER" \
    DBPASS="$MOODLE_DB_PASS" PREFIX="$MOODLE_DB_PREFIX" php -r '
        $dsn = sprintf("pgsql:host=%s;port=%s;dbname=%s",
            getenv("PGHOST"), getenv("PGPORT"), getenv("DBNAME"));
        try {
            $pdo = new PDO($dsn, getenv("DBUSER"), getenv("DBPASS"), [PDO::ATTR_ERRMODE => PDO::ERRMODE_SILENT]);
            $table = getenv("PREFIX") . "config";
            $stmt = $pdo->query("SELECT 1 FROM information_schema.tables WHERE table_name = " . $pdo->quote($table));
            if ($stmt && $stmt->fetchColumn()) {
                exit(0);
            }
        } catch (Exception $e) {}
        exit(1);
    '
}

main() {
    log "=== Moodle ${MOODLE_VERSION} provisioning started ==="
    create_database
    download_moodle

    # Was Moodle already installed before this run? Decides how plugins register.
    local was_installed=0
    if [ -f "${MOODLE_DIR}/config.php" ]; then
        if database_is_initialized; then
            was_installed=1
        else
            log "Stale config.php found with uninitialized database — removing config.php to run fresh CLI install."
            rm -f "${MOODLE_DIR}/config.php"
        fi
    fi

    install_plugins        # drop plugin code into the tree (before install/upgrade)
    fix_permissions
    install_moodle         # fresh install: also installs the bundled plugins

    if [ "$was_installed" = "1" ] && [ "$PLUGINS_CHANGED" = "1" ]; then
        upgrade_plugins    # existing install: register the newly added/updated ones
    fi

    purge_caches
    log "=== Moodle provisioning complete. Access it at ${MOODLE_WWWROOT} ==="
}

main "$@"
