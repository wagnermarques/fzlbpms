#!/bin/bash

#DOCKER_EXEC_IT="docker exec -it fzl-php8.3-fpm"
#$DOCKER_EXEC_IT php /var/www/html/moodle/admin/cli/reset_password.php --username=adminmoodle --password=1234

DOCKER_EXEC_IT="docker exec -it fzl-mysql"
SQL="UPDATE mdl_user SET password = MD5('1234') WHERE username = 'adminmoodle';"
CMD="mysql -uroot -p1234 -e \"$SQL\""


echo Running... $DOCKER_EXEC_IT $CMD

$DOCKER_EXEC_IT $CMD
