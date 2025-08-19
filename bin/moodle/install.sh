#!/bin/bash

echo "bin/moodle/install.sh running..."
_THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
# Caminho base do script para resolver os caminhos relativos corretamente

source $_THIS_DIR/../utils.sh
ECHO_PREFIX="bin/moodle/install.sh"
fzlecho $ECHO_PREFIX "Executando bin/moodle/install.sh from ${_THIS_DIR}"

# ------------------------------------------------------------------------------

# Este script para a execucao se qualquer comando falhar.
set -e

################################################################################
# --- VARIAVEIS DE CONFIGURACAO ---
################################################################################
fzlecho $ECHO_PREFIX "Definindo variavies de configuracao e de instalacao"

# Versao do Moodle a ser clonada do Git
MOODLE_VERSION_TO_CLONE="MOODLE_500_STABLE" # Moodle 5.0.x
MOODLE_ZIP_URL="https://packaging.moodle.org/stable500/moodle-latest-500.tgz"


# --- Configuraï¿½ï¿½es do Banco de Dados (para fzl-postgresql) ---
DB_TYPE="pgsql"
DB_HOST="fzl-postgresql" # Nome do servico PostgreSQL no Docker Compose
DB_NAME="moodle"         # Nome do banco de dados (deve existir no fzl-postgresql)
DB_USER="postgres"       # Usuario do banco de dados (deve existir no fzl-postgresql)
DB_PASSWORD="1234"       # Senha do banco de dados (deve existir no fzl-postgresql)




# Caminho no HOST onde os fontes do Moodle serï¿½o clonados
# Corresponde a ./src-projects/var_www/html/moodle
MOODLE_APP_HOST_PATH="$_THIS_DIR/../../src-projects/var_www/html/moodle"
fzlecho $ECHO_PREFIX "Definindo o caminho do Moodle no host: ${MOODLE_APP_HOST_PATH}"

# Caminho no HOST para o diretï¿½rio moodledata (FORA do webroot por seguranï¿½a)
# Corresponde a ./src-projects/moodledata
MOODLEDATA_HOST_PATH="$_THIS_DIR/../../src-projects/moodledata"
fzlecho $ECHO_PREFIX "Definindo o caminho do moodledata no host: ${MOODLEDATA_HOST_PATH}"



# --- Configuracoes de URL do Moodle ---
# URL base para acessar o Moodle (deve corresponder ao mapeamento de porta do Nginx)
MOODLE_WWWROOT="http://localhost/moodle" # Se Nginx na porta 80 do host
MOODLE_APP_PATH_IN_CONTAINER="/var/www/html/moodle" # Caminho DO Moodle dentro do container Nginx/PHP-FPM
MOODLE_DATAROOT_IN_CONTAINER="/var/www/moodledata" # Caminho DO moodledata dentro do container PHP-FPM

MOODLE_SITE_FULLNAME="EtecZL - Auxilio Domiciliar e PPs"
MOODLE_SITE_SHORTNAME="Escola Tecnica Estadual da Zona Leste"
MOODLE_ADMIN_USER="admin"
MOODLE_ADMIN_USER_PASS="SuaSenhaAdminForte" # MUDE ESTA SENHA!
MOODLE_SITE_FULLNAME="EtecZL PPPs"
MOODLE_SITE_SHORTNAME="Escola Tecnica Estadual Zona Leste (Progressï¿½es Parciais)"
MOODLE_ADMIN_EMAIL="wagnerdocri@gmail.com"


PHP_FPM_CONTAINER_NAME="fzl-php8.3-fpm" # Certifique-se que o nome corresponde ao docker-compose.yml
WEB_CONTAINER_USER="www-data" # Usuario do container PHP-FPM que executa o Moodle

# Usuï¿½rio do host que precisa ter acesso aos arquivos do Moodle
# dentro do php fpm sera o www-data
WEB_USER="wgn"
WEB_GROUP="wgn"

################################################################################
# --- INICIO DA EXECUCAO ---
################################################################################

# Define codigos de cores para uma saï¿½da mais legï¿½vel
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem Cor

echo -e "${YELLOW}Iniciando a instalaï¿½ï¿½o e configuraï¿½ï¿½o do Moodle...${NC}"


# --- 1. Instalar dependï¿½ncias no host ---
# checka se o git estï¿½ instalado caso nï¿½o esteja, informa o usuario do script e para a execucao
if ! command -v git &> /dev/null; then
    echo -e "${RED}Erro: Git nï¿½o estï¿½ instalado. Por favor, instale o Git antes de continuar.${NC}"
    exit 1
fi

# tornando-se root pra acessar src-projects que esta dentro do containter
sudo echo $(pwd)

# --- 2. Clonar as fontes do Moodle ---
echo -e "\n${YELLOW}[2/6] Verificando e obtendo o Moodle...${NC}"
if [ -d "$MOODLE_APP_HOST_PATH" ]; then
    # Se o diretï¿½rio jï¿½ existe, removemos para garantir um clone limpo
    # Isso ï¿½ importante para evitar conflitos de versï¿½o ou arquivos antigos
    # nao remove a pasta, simplesmente renomeia por seguranca
    echo -e "${YELLOW}O diretï¿½rio '${MOODLE_APP_HOST_PATH}' jï¿½ existe. Renomeando pasta antiga para um clone limpo...${NC}"
    #sudo mv $MOODLE_APP_HOST_PATH "${MOODLE_APP_HOST_PATH}_backup_$(date +%Y%m%d_%H%M%S)"    
    rm -rf "$MOODLE_APP_HOST_PATH"
fi

#echo "Clonando Moodle (versï¿½o ${MOODLE_VERSION_TO_CLONE})... Isso pode levar um momento."
#git clone -b "$MOODLE_VERSION_TO_CLONE" "https://github.com/moodle/moodle.git" "$MOODLE_APP_HOST_PATH"
#echo -e "${GREEN}Moodle clonado com sucesso em '${MOODLE_APP_HOST_PATH}'.${NC}"
echo "Baixando Moodle (versÃ£o estÃ¡vel 5.0)... Isso pode levar um momento."
MOODLE_ARCHIVE_PATH="/tmp/moodle.tgz"

# fazendo o download do Moodle usando curl
echo -e "${YELLOW}Baixando Moodle usando curl...${NC}"
echo "$MOODLE_ZIP_URL"
curl --fail --silent --show-error --max-time 600 -o "$MOODLE_ARCHIVE_PATH" "$MOODLE_ZIP_URL"

# Verifica o cÃ³digo de saÃ­da do curl. Se for diferente de 0, o download falhou.
if [ $? -ne 0 ]; then
  echo -e "${RED}Erro: O download do Moodle falhou. Verifique sua conexÃ£o ou a URL.${NC}"
  # Remove o arquivo incompleto para evitar erros futuros
  rm -f "$MOODLE_ARCHIVE_PATH"
  exit 1
fi


# Verifica se o download foi bem-sucedido
echo -e "${YELLOW}Verificando se o download do Moodle foi bem-sucedido...${NC}"
mkdir -p "$MOODLE_APP_HOST_PATH"
tar -xzf "$MOODLE_ARCHIVE_PATH" -C "$MOODLE_APP_HOST_PATH" --strip-components=1
rm "$MOODLE_ARCHIVE_PATH"

echo -e "${GREEN}Moodle baixado e extraÃ­do com sucesso em '${MOODLE_APP_HOST_PATH}'.${NC}"
 


# --- 3. Definir permissï¿½es no host (nao no container) para os arquivos Moodle clonados no host ---
echo -e "\n${YELLOW}[3/${TOTAL_STAGES}] Criando diretórios e definindo permissões no host...${NC}"
# Permissoes para o www-data acessar, mas sem permitir escrita generalizada
sudo chown -R ${WEB_USER}:${WEB_GROUP} "$MOODLE_APP_HOST_PATH" || echo -e "${YELLOW}Aviso: Nao foi possivel definir owner/group www-data. Verifique se o usuario existe no host ou ajuste manualmente.${NC}"
sudo find "$MOODLE_APP_HOST_PATH" -type d -exec chmod 0755 {} \; # Diretorios
sudo find "$MOODLE_APP_HOST_PATH" -type f -exec chmod 0644 {} \; # Arquivos
echo -e "${GREEN}Permissï¿½es de arquivos Moodle definidas.${NC}"



# --- 4. preparar banco de dados
# --- Verifica se o banco de dados jï¿½ existe
echo -e "\n${YELLOW}[5/6] Verificando se o banco de dados '${DB_NAME}' jï¿½ existe...${NC}"
if docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "\l" | grep -q "${DB_NAME}"; then
    echo -e "${YELLOW}O banco de dados '${DB_NAME}' jï¿½ existe. Banco sera renomeado.${NC}"
    # renomeando o banco de dados
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "ALTER DATABASE ${DB_NAME} RENAME TO ${DB_NAME}_old_$(date +%Y%m%d%H%M%S);"
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8' TEMPLATE template0;"
else
    echo -e "${YELLOW}O banco de dados '${DB_NAME}' nï¿½o existe. Criando o banco de dados...${NC}"
    docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8' TEMPLATE template0;"
    echo -e "${GREEN}Banco de dados '${DB_NAME}' criado com sucesso.${NC}"
fi

# --- 5. Listando bancos de dados para verificar se o moodle foi criado
echo -e "\n${YELLOW}[6/6] Listando bancos de dados para verificar se o moodle foi criado...${NC}"
docker exec -it fzl-postgresql psql -U "${DB_USER}" -d postgres -c "\l"
echo -e "\n${YELLOW}[6/6] Ajustando permissoes no banco criado ..${NC}"
docker exec -it fzl-postgresql psql -U postgres -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE moodle TO postgres;"


# --- criando o diretorio moodledata no container php-fpm
docker exec -it fzl-php8.3-fpm mkdir -p /var/www/moodledata
docker exec -it fzl-php8.3-fpm chown www-data:www-data -R /var/www/moodledata


# --- 4. Rodar o instalador CLI do Moodle no container php-fpm ---
# --- requisitos
# --- o container php-fpm deve estar rodando
# --- o diretorio onde o moodle foi clonado tem que estar montado dentro do container
# criaï¿½ï¿½o do dataroot e geraï¿½ï¿½o do config.php
echo -e "\n${YELLOW}[6/6] Rodando o instalador CLI do Moodle dentro do contï¿½iner PHP-FPM...${NC}"
echo "${YELLOW}Verifica se o container $PHP_FPM_CONTAINER_NAME esta rodando"
if ! docker ps --filter "name=${PHP_FPM_CONTAINER_NAME}" --format "{{.ID}}" | grep -q .; then
    echo -e "${RED}Erro: Contï¿½iner '${PHP_FPM_CONTAINER_NAME}' nao esta rodando. Por favor, inicie-o primeiro.${NC}"
    exit 1
fi

fzlecho $ECHO_PREFIX "Executando o instalador CLI do Moodle no container ${PHP_FPM_CONTAINER_NAME}..."
docker exec -it "${PHP_FPM_CONTAINER_NAME}" ls -la 


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

# 
echo -e "${GREEN}Instalador CLI do Moodle executado. Verifique a saida acima para erros.${NC}"

# --- 5. Definir permissoes www-data para arquivos do moodle e moodledata
# --- requisitos
# --- o container php-fpm deve estar rodando (ja verificado acima)
# --- o diretorio onde o moodle foi clonado tem que estar montado dentro do container (ja verificado acima)
# --- diretorios presentes /var/www/html/mooodle e /var/www/moodledata
fzlecho $ECHO_PREFIX " => Dando permissoes www-data dentro dos containers php-fpm e nginx"

fzlecho $ECHO_PREFIX MOODLE_APP_PATH_IN_CONTAINER=${MOODLE_APP_PATH_IN_CONTAINER}
docker exec -it "${PHP_FPM_CONTAINER_NAME}" ls -la "${MOODLE_APP_PATH_IN_CONTAINER}"

fzlecho $ECHO_PREFIX MOODLE_DATAROOT_IN_CONTAINER=${MOODLE_DATAROOT_IN_CONTAINER}
docker exec -it "${PHP_FPM_CONTAINER_NAME}" chown -R www-data:www-data "${MOODLE_APP_PATH_IN_CONTAINER}"
docker exec -it "${PHP_FPM_CONTAINER_NAME}" chown -R www-data:www-data "${MOODLE_DATAROOT_IN_CONTAINE}"

fzlecho $ECHO_PREFIX "Dando permissoes www-data no container nginx"
docker exec -it "fzl-nginx" chown -R nginx:nginx "${MOODLE_APP_PATH_IN_CONTAINER}" 

# --- X backup do config.php, rename as config-YYYYMMDD_HHMMSS.php
docker cp "${PHP_FPM_CONTAINER_NAME}:${MOODLE_APP_PATH_IN_CONTAINER}/config.php" $SCRIPT_DIR/config-$(date +%Y%m%d_%H%M%S).php

# no host tambem precisa dar permissao
sudo chown -R wgn:wgn "$MOODLE_APP_HOST_PATH"

sudo find "$MOODLE_APP_HOST_PATH" -type d -exec chmod 0755 {} \; # Diretorios
sudo find "$MOODLE_APP_HOST_PATH" -type f -exec chmod 0644 {} \; # Arquivos


# --- X dump do banco criado pela instalacao

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}ï¿½ Configuraï¿½ï¿½o do Moodle concluï¿½da!ï¿½ ${NC}"
echo -e "${GREEN}ï¿½ Acesse: ${MOODLE_WWWROOT}ï¿½ ï¿½ ï¿½ ï¿½ ï¿½ ${NC}"
echo -e "${GREEN}=========================================${NC}"
