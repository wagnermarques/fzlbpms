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

