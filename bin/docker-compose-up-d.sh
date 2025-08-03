#!/bin/bash

# a diferenca entre rodar docker compose up -d no diretorio do projeto e rodar
# rodar esse script eh que apois rodar docker compose up direto no diretorio
# nao ajusta permissao de pastas e arquivos no host
# rodando esse script ja roda chown e chmod nos volumes binded

source ./utils.sh

PREFIX="docker-compose-up-d.sh |"

fzlecho $PREFIX "Iniciando o docker-compose up -d..."
