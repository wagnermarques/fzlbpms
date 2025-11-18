#!/bin/bash

#usage : ./reset-password.sh moodlecontext username newpassword
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 moodlecontext username newpassword"
    exit 1
fi

MOODLE_CONTEXT=$1
USERNAME=$2
NEWPASSWORD=$3

DOCKER_EXEC_IT="docker exec -it fzl-php8.3-fpm"
CMD="php /var/www/html/$MOODLE_CONTEXT/admin/cli/reset_password.php --username=$USERNAME --password=$PASSWORD"

#DOCKER_EXEC_IT="docker exec -it fzl-mysql"
#SQL="UPDATE mdl_user SET password = MD5('s3cret') WHERE username = 'useradmin';"
#CMD="mysql -uroot -ps3cret -e \"$SQL\""


echo Running... $DOCKER_EXEC_IT $CMD

$DOCKER_EXEC_IT $CMD
