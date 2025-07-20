#!/bin/bash

# Este script para a execu��o se qualquer comando falhar.
set -e

################################################################################
# --- VARI�VEIS DE CONFIGURA��O ---
################################################################################

# Vers�o do Moodle a ser clonada do Git
MOODLE_VERSION_TO_CLONE="MOODLE_500_STABLE" # Moodle 5.0.x

# Caminho base do script para resolver os caminhos relativos corretamente
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# --- Configura��es do Banco de Dados (para fzl-postgresql) ---
DB_TYPE="pgsql"
DB_HOST="fzl-postgresql" # Nome do servico PostgreSQL no Docker Compose
DB_NAME="moodle"         # Nome do banco de dados (deve existir no fzl-postgresql)
DB_USER="postgres"         # Usu�rio do banco de dados (deve existir no fzl-postgresql)
DB_PASSWORD="1234"       # Senha do banco de dados (deve existir no fzl-postgresql)

# Caminho no HOST onde os fontes do Moodle ser�o clonados
# Corresponde a ./src-projects/var_www/html/moodle
MOODLE_APP_HOST_PATH="${SCRIPT_DIR}/../../src-projects/var_www/html/moodle"

# Caminho no HOST para o diret�rio moodledata (FORA do webroot por seguran�a)
# Corresponde a ./src-projects/moodledata
MOODLEDATA_HOST_PATH="${SCRIPT_DIR}/../../src-projects/moodledata"

# --- Configura��es de URL do Moodle ---
# URL base para acessar o Moodle (deve corresponder ao mapeamento de porta do Nginx)
MOODLE_WWWROOT="http://localhost/moodle" # Se Nginx na porta 80 do host
MOODLE_APP_PATH_IN_CONTAINER="/var/www/html/moodle" # Caminho DO Moodle dentro do cont�iner Nginx/PHP-FPM
MOODLE_DATAROOT_IN_CONTAINER="/var/www/moodledata" # Caminho DO moodledata dentro do cont�iner PHP-FPM

MOODLE_ADMIN_USER="admin"
MOODLE_ADMIN_USER_PASS="SuaSenhaAdminForte" # MUDE ESTA SENHA!
MOODLE_SITE_FULLNAME="EtecZL PPPs"
MOODLE_SITE_SHORTNAME="Escola Tecnica Estadual Zona Leste (Progress�es Parciais)"
MOODLE_ADMIN_EMAIL="wagnerdocri@gmail.com"


PHP_FPM_CONTAINER_NAME="fzl-php8.3-fpm" # Certifique-se que o nome corresponde ao docker-compose.yml

# Usu�rio do host que precisa ter acesso aos arquivos do Moodle
# dentro do php fpm sera o www-data
WEB_USER="wgn"
WEB_GROUP="wgn"

################################################################################
# --- IN�CIO DA EXECU��O ---
################################################################################

# Define c�digos de cores para uma sa�da mais leg�vel
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem Cor

echo -e "${YELLOW}Iniciando a instala��o e configura��o do Moodle...${NC}"


# --- 1. Instalar depend�ncias no host ---
# checka se o git est� instalado caso n�o esteja, informa o usuario do script e para a execucao
if ! command -v git &> /dev/null; then
    echo -e "${RED}Erro: Git n�o est� instalado. Por favor, instale o Git antes de continuar.${NC}"
    exit 1
fi


# --- 2. Clonar as fontes do Moodle ---
echo -e "\n${YELLOW}[2/6] Verificando e clonando o Moodle...${NC}"
if [ -d "$MOODLE_APP_HOST_PATH" ]; then
    # Se o diret�rio j� existe, removemos para garantir um clone limpo
    # Isso � importante para evitar conflitos de vers�o ou arquivos antigos
    # nao remove a pasta, simplesmente renomeia por seguranca
    echo -e "${YELLOW}O diret�rio '${MOODLE_APP_HOST_PATH}' j� existe. Renomeando pasta antiga para um clone limpo...${NC}"
    sudo mv $MOODLE_APP_HOST_PATH "${MOODLE_APP_HOST_PATH}_backup_$(date +%Y%m%d_%H%M%S)"
fi

echo "Clonando Moodle (vers�o ${MOODLE_VERSION_TO_CLONE})... Isso pode levar um momento."
git clone -b "$MOODLE_VERSION_TO_CLONE" "https://github.com/moodle/moodle.git" "$MOODLE_APP_HOST_PATH"
echo -e "${GREEN}Moodle clonado com sucesso em '${MOODLE_APP_HOST_PATH}'.${NC}"



# --- 3. Definir permiss�es no host (nao no container) para os arquivos Moodle clonados no host ---
echo -e "\n${YELLOW}[5/6] Definindo permiss�es iniciais para os arquivos do Moodle no host...${NC}"
# Permissoes para o www-data acessar, mas sem permitir escrita generalizada
sudo chown -R ${WEB_USER}:${WEB_GROUP} "$MOODLE_APP_HOST_PATH" || echo -e "${YELLOW}Aviso: Nao foi possivel definir owner/group www-data. Verifique se o usuario existe no host ou ajuste manualmente.${NC}"
sudo find "$MOODLE_APP_HOST_PATH" -type d -exec chmod 0755 {} \; # Diretorios
sudo find "$MOODLE_APP_HOST_PATH" -type f -exec chmod 0644 {} \; # Arquivos
echo -e "${GREEN}Permiss�es de arquivos Moodle definidas.${NC}"


# --- 4. preparar banco de dados
# --- Verifica se o banco de dados j� existe
echo -e "\n${YELLOW}[5/6] Verificando se o banco de dados '${DB_NAME}' j� existe...${NC}"
if docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "\l" | grep -q "${DB_NAME}"; then
    echo -e "${YELLOW}O banco de dados '${DB_NAME}' j� existe. Banco sera renomeado.${NC}"
    # renomeando o banco de dados
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "ALTER DATABASE ${DB_NAME} RENAME TO ${DB_NAME}_old;"
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8' TEMPLATE template0;"
else
    echo -e "${YELLOW}O banco de dados '${DB_NAME}' n�o existe. Criando o banco de dados...${NC}"
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8' TEMPLATE template0;"
    echo -e "${GREEN}Banco de dados '${DB_NAME}' criado com sucesso.${NC}"
fi

# --- 5. Listando bancos de dados para verificar se o moodle foi criado
echo -e "\n${YELLOW}[6/6] Listando bancos de dados para verificar se o moodle foi criado...${NC}"
docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "\l"
echo -e "\n${YELLOW}[6/6] Ajustando permissoes no banco criado ..${NC}"
docker exec -it fzl-postgresql psql -U postgres -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE moodle TO postgres;"

# --- 4. Rodar o instalador CLI do Moodle no container php-fpm ---
# --- requisitos
# --- o container php-fpm deve estar rodando
# --- o diretorio onde o moodle foi clonado tem que estar montado dentro do container
# cria��o do dataroot e gera��o do config.php
echo -e "\n${YELLOW}[6/6] Rodando o instalador CLI do Moodle dentro do cont�iner PHP-FPM...${NC}"
echo "${YELLOW}Verifica se o cont�iner $PHP_FPM_CONTAINER_NAME est� rodando"
if ! docker ps --filter "name=${PHP_FPM_CONTAINER_NAME}" --format "{{.ID}}" | grep -q .; then
    echo -e "${RED}Erro: Cont�iner '${PHP_FPM_CONTAINER_NAME}' n�o est� rodando. Por favor, inicie-o primeiro.${NC}"
    exit 1
fi

docker exec -it "${PHP_FPM_CONTAINER_NAME}" php "${MOODLE_APP_PATH_IN_CONTAINER}/admin/cli/install.php" \
  --lang=pt_br \
  --wwwroot="${MOODLE_WWWROOT}" \
  --dataroot="${MOODLE_DATAROOT_IN_CONTAINER}" \
  --dbtype="${DB_TYPE}" \
  --dbhost="${DB_HOST}" \
  --dbname="${DB_NAME}" \
  --dbuser="${DB_USER}" \
  --dbpass="${DB_PASSWORD}" \
  --prefix="mdl_" \
  --adminuser="${MOODLE_ADMIN_USER}" \
  --adminpass="${MOODLE_ADMIN_USER_PASS}" \
  --fullname="${MOODLE_SITE_FULLNAME}" \
  --shortname="${MOODLE_SITE_SHORTNAME}" 


echo -e "${GREEN}Instalador CLI do Moodle executado. Verifique a sa�da acima para erros.${NC}"


# --- 5. Definir propriedade www-data para arquivos do moodle e moodledata
# --- requisitos
# --- o container php-fpm deve estar rodando (ja verificado acima)
# --- o diretorio onde o moodle foi clonado tem que estar montado dentro do container (ja verificado acima)
# --- diretorios presentes /var/www/html/mooodle e /var/www/moodledata
#echo -e "\n${YELLOW}[6/6] Rodando o instalador CLI do Moodle dentro do cont�iner PHP-FPM...${NC}"
#echo "${YELLOW}Verifica se o cont�iner $PHP_FPM_CONTAINER_NAME est� rodando"


# --- X backup do config.php
# --- X dump do banco criado pela instalacao


echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}� Configura��o do Moodle conclu�da!� ${NC}"
echo -e "${GREEN}� Acesse: ${MOODLE_WWWROOT}� � � � � ${NC}"
echo -e "${GREEN}=========================================${NC}"
