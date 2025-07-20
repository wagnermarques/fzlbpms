# Interrompe o script se qualquer comando falhar
set -e

# --- Variáveis de Configuração ---
# Nome do serviço no docker-compose.yml
SERVICE_NAME="fzl-postgresql"
# Nome do banco de dados que queremos criar
DB_NAME="moodle"
# Usuário do PostgreSQL com permissão para criar bancos (o superusuário)
DB_USER="postgres"
# ---------------------------------

echo "Verificando se o banco de dados '$DB_NAME' já existe no serviço '$SERVICE_NAME'..."

# Comando para verificar a existência do banco.
# Ele executa um SELECT que retorna '1' se o banco existir e nada se não existir.
# As flags -tA do psql removem cabeçalhos e alinhamento para uma saída limpa.
DB_EXISTS=$(docker compose exec "$SERVICE_NAME" psql -U "$DB_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")

# Verifica o resultado do comando anterior
if [ "$DB_EXISTS" = "1" ]; then
    echo "O banco de dados '$DB_NAME' já existe. Nenhuma ação necessária."
else
    echo "O banco de dados '$DB_NAME' não existe. Criando agora..."
    docker compose exec "$SERVICE_NAME" psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME"
    echo "Banco de dados '$DB_NAME' criado com sucesso!"
fi

echo "Script concluído."
