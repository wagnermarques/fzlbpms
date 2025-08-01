#!/bin/bash

# --- Este script de entrypoint é executado quando o contêiner inicia. ---
# Executa o comando original (CMD) para iniciar o PHP-FPM
#echo "-> Iniciando o PHP-FPM..."
exec "$@"