#!/bin/bash
# Interrompe o script se qualquer comando falhar
set -e

# --- Variáveis de Configuração ---
# Nome do serviço no docker-compose.yml
SERVICE_NAME="fzl-postgresql"
# Usuário superusuário do PostgreSQL para realizar as operações
SUPERUSER="postgres"

# --- Validação dos Argumentos ---
if [ "$#" -ne 3 ]; then
    echo "Uso: $0 <db_name> <db_user> <db_pass>"
    exit 1
fi

DB_NAME=$1
DB_USER=$2
DB_PASS=$3

# --- Funções ---

# Função para verificar se um usuário existe
user_exists() {
    docker compose exec "$SERVICE_NAME" psql -U "$SUPERUSER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='$1'"
}

# Função para verificar se um banco de dados existe
database_exists() {
    docker compose exec "$SERVICE_NAME" psql -U "$SUPERUSER" -tAc "SELECT 1 FROM pg_database WHERE datname='$1'"
}

# --- Lógica Principal ---

echo "Iniciando configuração do banco de dados para Moodle..."

# 1. Verificar/Criar usuário
echo "Verificando se o usuário '$DB_USER' já existe..."
if [ "$(user_exists "$DB_USER")" = "1" ]; then
    echo "Usuário '$DB_USER' já existe. Nenhuma ação necessária para o usuário."
else
    echo "Usuário '$DB_USER' não existe. Criando agora..."
    docker compose exec "$SERVICE_NAME" psql -U "$SUPERUSER" -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';"
    echo "Usuário '$DB_USER' criado com sucesso!"
fi

# 2. Verificar/Criar banco de dados
echo "Verificando se o banco de dados '$DB_NAME' já existe..."
if [ "$(database_exists "$DB_NAME")" = "1" ]; then
    echo "Banco de dados '$DB_NAME' já existe."
else
    echo "Banco de dados '$DB_NAME' não existe. Criando agora..."
    docker compose exec "$SERVICE_NAME" psql -U "$SUPERUSER" -c "CREATE DATABASE $DB_NAME;"
    echo "Banco de dados '$DB_NAME' criado com sucesso!"
fi

# 3. Conceder privilégios
echo "Concedendo todos os privilégios no banco de dados '$DB_NAME' para o usuário '$DB_USER'..."
docker compose exec "$SERVICE_NAME" psql -U "$SUPERUSER" -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
echo "Privilégios concedidos com sucesso!"

echo "Script de configuração do banco de dados concluído."