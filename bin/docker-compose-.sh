#!/bin/bash

# --- Este script para reconstruir um serviço Docker Compose. ---
#
# Uso: ./rebuild_service.sh [NOME_DO_SERVIÇO]
#
# Exemplo: ./rebuild_service.sh fzl-nexus
#
# O script irá:
# 1. Parar e remover o contêiner do serviço especificado.
# 2. Reconstruir a imagem do serviço sem cache.
# 3. Iniciar o contêiner do serviço em modo detached.

# Verifica se o nome do serviço foi fornecido como argumento
if [ -z "$1" ]; then
  echo "Erro: Por favor, forneça o nome do serviço para reconstruir."
  echo "Uso: $0 [NOME_DO_SERVIÇO]"
  exit 1
fi

SERVICE_NAME=$1

# 1. Executa docker compose down para o serviço.
#    O --remove-orphans é útil caso o nome do serviço tenha mudado.
echo "-> Parando e removendo o contêiner do serviço '$SERVICE_NAME'..."
docker compose down --remove-orphans --volumes $SERVICE_NAME

# Verifica se o comando down foi bem-sucedido
if [ $? -ne 0 ]; then
  echo "Erro ao executar 'docker compose down' para o serviço '$SERVICE_NAME'."
  exit 1
fi

# 2. Executa docker compose build com --no-cache para o serviço.
echo "-> Reconstruindo a imagem do serviço '$SERVICE_NAME' sem cache..."
docker compose build --no-cache $SERVICE_NAME

# Verifica se o comando build foi bem-sucedido
if [ $? -ne 0 ]; then
  echo "Erro ao executar 'docker compose build' para o serviço '$SERVICE_NAME'."
  exit 1
fi

# 3. Executa docker compose up para o serviço em modo detached.
echo "-> Iniciando o contêiner do serviço '$SERVICE_NAME'..."
docker compose up -d $SERVICE_NAME

# Verifica se o comando up foi bem-sucedido
if [ $? -ne 0 ]; then
  echo "Erro ao executar 'docker compose up' para o serviço '$SERVICE_NAME'."
  exit 1
fi

echo "Sucesso: O serviço '$SERVICE_NAME' foi reconstruído e iniciado."

docker logs -f $SERVICE_NAME &

exit 0