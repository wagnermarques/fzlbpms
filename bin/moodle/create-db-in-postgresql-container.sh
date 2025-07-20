# Interrompe o script se qualquer comando falhar
set -e

# --- Vari�veis de Configura��o ---
# Nome do servi�o no docker-compose.yml
SERVICE_NAME="fzl-postgresql"
# Nome do banco de dados que queremos criar
DB_NAME="moodle"
# Usu�rio do PostgreSQL com permiss�o para criar bancos (o superusu�rio)
DB_USER="postgres"
# ---------------------------------

echo "Verificando se o banco de dados '$DB_NAME' j� existe no servi�o '$SERVICE_NAME'..."

# Comando para verificar a exist�ncia do banco.
# Ele executa um SELECT que retorna '1' se o banco existir e nada se n�o existir.
# As flags -tA do psql removem cabe�alhos e alinhamento para uma sa�da limpa.
DB_EXISTS=$(docker compose exec "$SERVICE_NAME" psql -U "$DB_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")

# Verifica o resultado do comando anterior
if [ "$DB_EXISTS" = "1" ]; then
    echo "O banco de dados '$DB_NAME' j� existe. Nenhuma a��o necess�ria."
else
    echo "O banco de dados '$DB_NAME' n�o existe. Criando agora..."
    docker compose exec "$SERVICE_NAME" psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME"
    echo "Banco de dados '$DB_NAME' criado com sucesso!"
fi

echo "Script conclu�do."
