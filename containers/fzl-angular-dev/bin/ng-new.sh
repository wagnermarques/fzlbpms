#!/bin/bash

#PARA OS PATHS DESTE SCRIPT FUNCIONARREM
#RODA-LOS COM O COMANDO: ./bin/ng.sh <nome-do-projeto>

# Encerra o script imediatamente se um comando falhar
set -e
source ./.env


# --- 1. Valida��o do Input ---
if [ -z "$1" ]; then
  echo "Erro: O nome do projeto n�o foi fornecido."
  echo "Uso: $0 <nome-do-projeto>"
  exit 1
fi


# Valida se as vari�veis necess�rias do .env foram carregadas
if [ -z "$DOCKER_CONTAINER_NAME" ] || [ -z "$DOCKER_IMG_NAME" ]; then
  echo "Erro: Verifique se DOCKER_CONTAINER_NAME e DOCKER_IMAGE_NAME est�o definidos no arquivo .env"
  exit 1
fi


# --- 3. L�gica Principal: Verificar Cont�iner e Executar ---
echo "Verificando o status do cont�iner: $DOCKER_CONTAINER_NAME..."

declare project_name=$1

# O diret�rio onde o projeto ser� criado, relativo ao workdir do container (/apps)
TARGET_DIRECTORY="apps" #na verdade o src-projects ta montado no /apps (dentro do container � apps e fora � src-projects)

# Isso passa o nome e o diret�rio de forma separada e correta para o Angular CLI
NG_NEW_COMMAND="ng new ${project_name} --directory ${project_name} --routing=true --style=css --ssr=false"


function roda_ng_new_with_docker_exec(){
      echo "Cont�iner encontrado em execu��o (ID: $RUNNING_CONTAINER_ID). Usando 'docker exec'..."
      docker exec -it "$RUNNING_CONTAINER_ID" /bin/sh -c "$NG_NEW_COMMAND"
}

function roda_ng_new_with_docker_run(){
    echo "Cont�iner n�o est� em execu��o. Usando 'docker run' com um cont�iner tempor�rio..."
    docker run --rm -it -v "$(pwd)/src-projects":/apps -w /apps "$DOCKER_IMG_NAME" /bin/sh -c "$NG_NEW_COMMAND"
}


# Procura por um cont�iner em execu��o com o nome exato (^...$)
RUNNING_CONTAINER_ID=$(docker ps -q -f "name=^${DOCKER_CONTAINER_NAME}$")

if [ -n "$RUNNING_CONTAINER_ID" ]; then
    # Se a vari�vel RUNNING_CONTAINER_ID n�o for nula, o cont�iner est� rodando
    roda_ng_new_with_docker_exec
else
    # Se a vari�vel for nula, o cont�iner n�o est� rodando
    roda_ng_new_with_docker_run
fi

# --- 4. P�s-execu��o: Corrigir Permiss�es ---
echo ""
echo "Projeto '$project_name' criado com sucesso em ../src-projects/"
echo "Corrigindo permiss�es de arquivo..."

# Usa sudo para garantir que a posse dos arquivos criados pelo cont�iner seja do usu�rio atual
sudo chown -R $USER:$USER $(pwd)/src-projects/$project_name

echo "Permiss�es corrigidas. Processo conclu�do!"

