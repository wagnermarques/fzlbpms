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
MOODLEDATA_DIR="${MOODLEDATA_DIR:-/var/www/moodledata}"
WEB_USER="${WEB_USER:-www-data}"

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
    if [ -f "${MOODLE_DIR}/version.php" ]; then
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
}

purge_caches() {
    local cli; cli="$(cli_path)"
    if [ -f "${MOODLE_DIR}/${cli}/purge_caches.php" ]; then
        su -s /bin/bash -c "cd '${MOODLE_DIR}' && php ${cli}/purge_caches.php" "$WEB_USER" || true
    fi
}

main() {
    log "=== Moodle ${MOODLE_VERSION} provisioning started ==="
    create_database
    download_moodle
    fix_permissions
    install_moodle
    purge_caches
    log "=== Moodle provisioning complete. Access it at ${MOODLE_WWWROOT} ==="
}

main "$@"
