#!/bin/bash
set -e # Encerra o script se um comando falhar

echo "==== INSPECIONANDO PERMISS�ES ANTES DO CHOWN ===="
ls -la /opt/karaf

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

