#!/bin/bash

# --- Este script de entrypoint é executado quando o contêiner inicia. ---
# Executa o comando original (CMD) para iniciar o PHP-FPM
source /usr/local/bin/utils.sh
echo .
echo .

PREFIX=" php8.3-fpm docker-entrypoint.sh"

fzlecho $PREFIX "Iniciando o entrypoint do contaiiner..."
echo .

fzlecho $PREFIX "Ajustando inicialmente as permissoes em /var/www e /var/www/html"
chown -R www-data:www-data /var/www
chown -R www-data:www-data /var/www/html

fzlecho $PREFIX "Ajustando as permissoes de escrita em /var/www/html"
chmod -R 775 /var/www/html

fzlecho $PREFIX "Ajustando as permissoes de escrita em /var/www"
chmod -R 775 /var/www

fzlecho $PREFIX "Permissoes ajustadas com sucesso!"
ls -la /var/www/html
ls -la /var/www
ls -la /var/


# Verifica se o comando passado é vazio
if [ -z "$1" ]; then
    fzlecho $PREFIX "Nenhum comando fornecido. Iniciando o PHP-FPM padrão..."
    fzlecho $PREFIX "por enquanto nao sera feito nada"
else
    fzlecho $PREFIX "Comando fornecido: $1"
    fzlecho $PREFIX "-> Iniciando o comando fornecido..."
    exec "$@"
fi  
