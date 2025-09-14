#!/bin/bash

# Nome do container PHP-FPM do seu ambiente Docker
CONTAINER_NAME="fzl-php8.3-fpm"

# Caminho do script cron do Moodle dentro do container
MOODLE_CRON_PATH="/var/www/html/admin/cli/cron.php"

# Caminho do executável do PHP dentro do container
PHP_BIN_PATH="/usr/local/bin/php"

# Comando do cron a ser adicionado
CRON_JOB="* * * * * ${PHP_BIN_PATH} ${MOODLE_CRON_PATH} >/dev/null 2>&1"

echo "Iniciando a configuração do cron job para o container ${CONTAINER_NAME}..."

# Verifica se o container está rodando
if ! docker ps --filter "name=${CONTAINER_NAME}" --quiet | grep -q .; then
  echo "Erro: O container ${CONTAINER_NAME} não está em execução. Certifique-se de que ele está ativo e tente novamente."
  exit 1
fi

echo "Entrando no conteiner e verificando a configuração do crontab..."

# Executa os comandos dentro do container
docker exec -it "${CONTAINER_NAME}" bash -c "
  # Verifica se o crontab já contém o comando do Moodle
  if crontab -l | grep -q '${CRON_JOB}'; then
    echo 'O cron job do Moodle já está configurado. Nenhuma alteração é necessária.'
  else
    # Adiciona o comando do cron no crontab
    (crontab -l 2>/dev/null; echo '${CRON_JOB}') | crontab -
    echo 'Cron job do Moodle adicionado com sucesso!'
  fi
"

echo "Configuração finalizada."
echo "Aguarde alguns minutos e verifique a interface do Moodle para confirmar se a notificação desapareceu."
