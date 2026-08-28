#!/bin/bash

# --- Este script de entrypoint é executado quando o contêiner inicia. ---
# Executa o comando original (CMD) para iniciar o PHP-FPM
source /usr/local/bin/utils.sh
echo .
echo .

PREFIX=" php8.3-fpm docker-entrypoint.sh"

fzlecho $PREFIX "Iniciando o entrypoint do contaiiner..."
echo .

# Trusts the local mkcert CA if ansible/setup-project.yml (--tags certs) has populated
# /usr/local/share/ca-certificates/mkcert (bind-mounted from
# containers/fzl-php8.3-fpm/certs) — safe no-op otherwise. Needed for
# curl/Guzzle calls to https://fzlbpms.local (Moodle's OAuth2 discovery and
# token exchange, made directly from this container).
fzlecho $PREFIX "Atualizando certificados CA confiaveis (mkcert, se presente)..."
update-ca-certificates

fzlecho $PREFIX "Ajustando inicialmente as permissoes em /var/www e /var/www/html"
chown -R www-data:www-data /var/www
chown -R www-data:www-data /var/www/html

fzlecho $PREFIX "Ajustando as permissoes de escrita em /var/www/html"
chmod -R 777 /var/www/html

fzlecho $PREFIX "Ajustando as permissoes de escrita em /moodledata"
chown -R www-data:www-data /moodledata
chmod -R 777 /moodledata

fzlecho $PREFIX "Ajustando as permissoes de escrita em /var/www"
chmod -R 777 /var/www

fzlecho $PREFIX "Permissoes ajustadas com sucesso!"
ls -la /var/www/html
ls -la /var/www
ls -la /var/
ls -la /moodledata


# Verifica se o comando passado é vazio
if [ -z "$1" ]; then
    fzlecho $PREFIX "Nenhum comando fornecido. Iniciando o PHP-FPM padrão..."
    fzlecho $PREFIX "por enquanto nao sera feito nada"
else
    fzlecho $PREFIX "Comando fornecido: $1"
    fzlecho $PREFIX "-> Iniciando o comando fornecido..."
    exec "$@"
fi  
