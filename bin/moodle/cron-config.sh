#!/bin/bash

# ==============================================================================
# Script Interativo para Configurar o Cron do Moodle
#
# Este script ajuda a adicionar a tarefa cron do Moodle ao crontab do sistema
# de forma segura. Ele deve ser executado com privilégios de root (usando sudo).
# Data: 27 de Agosto de 2025
# ==============================================================================

# Cores para a saída
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem Cor

echo -e "${GREEN}--- Configurador de Cron para Moodle ---${NC}"
echo "Este script irá ajudá-lo a configurar a tarefa agendada (cron) do Moodle."
echo ""

# --- 1. Verificar se o script está sendo executado como root ---
if [[ "$(id -u)" -ne 0 ]]; then
  echo -e "${RED}ERRO: Este script precisa ser executado como root ou com 'sudo'.${NC}"
  echo "Exemplo: sudo ./config_moodle_cron.sh"
  exit 1
fi

_MOODLE_PATH="/var/www/html/moodle"
_CRON_COMMAND="* * * * * www-data php -f $MOODLE_PATH/admin/cli/cron.php >/dev/null"

echo -e "O comando a ser adicionado ao crontab será:"
echo -e "${YELLOW}$CRON_COMMAND${NC}"
echo ""

read -p "As informações estão corretas e você deseja continuar? [S/n]: " CONFIRM
if [[ "$CONFIRM" != "" && "${CONFIRM^^}" != "S" ]]; then
  echo "Operação cancelada."
  exit 0
fi

# --- 4. Adicionar o comando ao crontab de forma segura ---
echo "Verificando se já existe uma tarefa cron para o Moodle..."

# Usar `crontab -u` para gerenciar o crontab do usuário específico
# `2>/dev/null` suprime o erro caso o usuário não tenha um crontab ainda
EXISTING_CRON=$(crontab -u "www-data" -l 2>/dev/null | grep 'admin/cli/cron.php')

if [[ -n "$EXISTING_CRON" ]]; then
  echo "Uma tarefa cron para o Moodle já existe:"
  echo -e "${YELLOW}$EXISTING_CRON${NC}"
  read -p "Deseja substituí-la pela nova configuração? [S/n]: " REPLACE_CONFIRM
  if [[ "$REPLACE_CONFIRM" != "" && "${REPLACE_CONFIRM^^}" != "S" ]]; then
    echo "Operação cancelada. Nenhuma alteração foi feita."
    exit 0
  fi
fi

# Adiciona a nova tarefa de forma segura, removendo a antiga primeiro
# para evitar duplicatas.
(crontab -u "www-data" -l 2>/dev/null | grep -v 'admin/cli/cron.php'; echo "$CRON_COMMAND") | crontab -u "www-data" -

if [[ $? -eq 0 ]]; then
  echo -e "${GREEN}SUCESSO! A tarefa cron do Moodle foi configurada para o usuário 'www-data'.${NC}"
  echo "Ela será executada a cada minuto para processar as tarefas pendentes."
else
  echo -e "${RED}ERRO: Falha ao tentar configurar o crontab.${NC}"
  exit 1
fi

echo ""
echo "Verificação final: o crontab do usuário 'www-data' agora contém:"
crontab -u "www-data" -l | grep 'admin/cli/cron.php'
echo ""
echo -e "${GREEN}Configuração concluída!${NC}"
