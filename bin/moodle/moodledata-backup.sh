#!/bin/bash
DOCKEREXEC="docker exec -it" 
CONTAINER_NAME="fzl-php8.3-fpm"
$DOCKEREXEC $CONTAINER_NAME ls -la /var/www