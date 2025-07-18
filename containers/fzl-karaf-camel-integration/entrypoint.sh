#!/bin/bash
set -e # Encerra o script se um comando falhar

echo "==== INSPECIONANDO PERMISSÕES ANTES DO CHOWN ===="
ls -la /opt/karaf

echo "==== EXECUTANDO CHOWN EM $KARAF_HOME ===="
chown -R appuser:appuser "$KARAF_HOME"

echo "==== INSPECIONANDO PERMISSÕES DEPOIS DO CHOWN ===="
ls -la /opt/karaf

# O comando abaixo mantém o contêiner rodando para debug.
# Comente a linha 'exec' por enquanto.
#echo "==== MODO DE DEBUG ATIVADO. CONTAINER EM ESPERA. ===="
#sleep infinity

echo "=== STARGIN KARAF ===="
exec gosu appuser "$@"

