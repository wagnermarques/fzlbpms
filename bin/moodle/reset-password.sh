#!/bin/bash

DOCKER_EXEC_IT="docker exec -it fzl-php8.3-fpm"
CMD="php /var/www/html/moodle/admin/cli/reset_password.php --username=useradmin --password=s3cret"

#DOCKER_EXEC_IT="docker exec -it fzl-mysql"
#SQL="UPDATE mdl_user SET password = MD5('s3cret') WHERE username = 'useradmin';"
#CMD="mysql -uroot -ps3cret -e \"$SQL\""


echo Running... $DOCKER_EXEC_IT $CMD

$DOCKER_EXEC_IT $CMD
