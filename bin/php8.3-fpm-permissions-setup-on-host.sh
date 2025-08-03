#!/bin/bash

echo " => php8.3-fpm-permissions-setup-on-host.sh running..."

_THIS_DIR=$(dirname "$(readlink -f "$0")")
source $_THIS_DIR/utils.sh

PREFIX="HOST-PERMISSIONS-SETUP"

fzlecho $PREFIX "Ajustando as permissoes da pasta no host montada no container em /var/www/html"
fzlecho $PREFIX "Permissoes atuais antes de serem configuradas"
ls -la $_THIS_DIR/../src-projects/var_www

fzlecho $PREFIX "Ajustando as permissoes de escrita em $_THIS_DIR/../src-projects/var_www"

sudo chmod -R 777 $_THIS_DIR/../src-projects/var_www

fzlecho $PREFIX "Permissoes ajustadas com sucesso! $_THIS_DIR/../src-projects/var_www"
ls -la $_THIS_DIR/../src-projects/var_www
