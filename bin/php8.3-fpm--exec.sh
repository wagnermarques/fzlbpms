#!/bin/bash

_CMD=$1

# This script is used to execute the PHP-FPM bash command inside container for PHP 8.3
docker exec -it fzl-php8.3-fpm bash -c "$_CMD"

