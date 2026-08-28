#!/bin/bash
set -e # Encerra o script se um comando falhar

echo "==== INSPECIONANDO PERMISS�ES ANTES DO CHOWN ===="
ls -la /opt/karaf

# ---------------------------------------------------------------------------
# Karaf JAAS realm (etc/users.properties) — generated here, never committed.
#
# This one file guards the SSH console (8101), JMX/RMI (1099, 44444) and the
# HTTP Basic realm in front of the Felix web console and Hawtio (8181). It
# used to ship in git with a default password, which meant rotating the
# credential required a commit — and the old value stayed in the history of a
# public repository forever.
#
# Now it is written at container start from FZL_KARAF_USER / FZL_KARAF_PASSWORD,
# which docker-compose.yml passes from .env (gitignored), exactly like the 15
# other secrets this service already consumes. nginx reads the SAME two
# variables to build the Authorization header it presents towards
# /system/console and /hawtio (containers/fzl-nginx/docker-entrypoint.d/
# 11-karaf-basic-auth.sh), so the two can never drift apart.
#
# To rotate: change FZL_KARAF_PASSWORD in .env, then
#   docker compose up -d --force-recreate fzl-karaf-camel-integration fzl-nginx
#
# Runs as root, before the gosu below, so it can write into $KARAF_HOME/etc;
# the chown that follows hands the file to appuser.
# ---------------------------------------------------------------------------
echo "==== GENERATING KARAF JAAS REALM (etc/users.properties) ===="
if [ -n "${FZL_KARAF_USER:-}" ] && [ -n "${FZL_KARAF_PASSWORD:-}" ]; then
  cat > "$KARAF_HOME/etc/users.properties" <<USERS_EOF
# GENERATED AT CONTAINER START by entrypoint.sh — do not edit, do not commit.
# Source of truth: FZL_KARAF_USER / FZL_KARAF_PASSWORD in .env
${FZL_KARAF_USER} = ${FZL_KARAF_PASSWORD},_g_:admingroup
_g_\\:admingroup = group,admin,manager,viewer,systembundles,ssh
USERS_EOF
  chmod 600 "$KARAF_HOME/etc/users.properties"
  echo "  Wrote etc/users.properties for user '${FZL_KARAF_USER}'."
else
  echo "  FATAL: FZL_KARAF_USER / FZL_KARAF_PASSWORD are unset."
  echo "  Set them in .env — see .env.template. Refusing to start with no"
  echo "  credential rather than falling back to a default one."
  exit 1
fi

echo "==== EXECUTANDO CHOWN EM $KARAF_HOME ===="
chown -R appuser:appuser "$KARAF_HOME"

echo "==== INSPECIONANDO PERMISS�ES DEPOIS DO CHOWN ===="
ls -la /opt/karaf

# O comando abaixo mant�m o cont�iner rodando para debug.
# Comente a linha 'exec' por enquanto.
#echo "==== MODO DE DEBUG ATIVADO. CONTAINER EM ESPERA. ===="
#sleep infinity

# Trust the local mkcert CA so Camel routes can call https://fzlbpms.local
# (Moodle web services) with TLS verification. Runs as root (we're still
# root here, before gosu) so it can write the JDK's cacerts directly. The CA
# is bind-mounted from containers/fzl-karaf-camel-integration/certs, staged
# by ansible/setup-project.yml (--tags certs). Safe no-op when absent.
MKCERT_CA="/run/mkcert-certs/mkcert-ca.pem"
if [ -f "$MKCERT_CA" ]; then
  echo "==== TRUSTING LOCAL mkcert CA IN THE JVM ===="
  if "${JAVA_HOME}/bin/keytool" -importcert -noprompt -trustcacerts \
       -alias mkcert-local-ca -file "$MKCERT_CA" \
       -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit 2>/dev/null; then
    echo "  Imported mkcert CA into ${JAVA_HOME}/lib/security/cacerts."
  else
    echo "  mkcert CA already present (or import skipped)."
  fi
else
  echo "==== No mkcert CA at $MKCERT_CA — https://fzlbpms.local calls will fail TLS verification. Run: ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml --tags certs -K ===="
fi

echo "==== DEPLOYING BLUEPRINT XML BUNDLES ===="
# Copy every Blueprint XML from the git-tracked source directory into
# Karaf's hot-deploy folder.  Karaf picks them up automatically on startup.
# Source: src-projects/karaf_bundles/blueprint-xmls-bundles (host)
#         → /opt/karaf/deploy_bundles/blueprint-xmls-bundles (container, read-only mount)
# Target: /opt/karaf/deploy  (Karaf's hot-deploy folder)
BLUEPRINT_SRC="/opt/karaf/deploy_bundles/blueprint-xmls-bundles"
if [ -d "$BLUEPRINT_SRC" ]; then
  xml_count=$(find "$BLUEPRINT_SRC" -maxdepth 1 -name "*.xml" | wc -l)
  if [ "$xml_count" -gt 0 ]; then
    cp "$BLUEPRINT_SRC"/*.xml /opt/karaf/deploy/
    echo "  Deployed $xml_count blueprint XML(s) from $BLUEPRINT_SRC"
  else
    echo "  No *.xml files found in $BLUEPRINT_SRC — skipping"
  fi
else
  echo "  WARNING: $BLUEPRINT_SRC not mounted — no blueprint XMLs deployed"
fi

echo "=== STARTING KARAF ===="
exec gosu appuser "$@"

