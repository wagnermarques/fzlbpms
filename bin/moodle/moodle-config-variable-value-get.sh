#!/bin/bash
moodle_context=$1
variable_name=$2
docker exec -it fzl-php8.3-fpm php "/var/www/html/$moodle_context/admin/cli/cfg.php" --name=$variable_name
