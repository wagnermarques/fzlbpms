#!/bin/bash

docker exec -it fzl-php8.3-fpm php /var/www/html/moodle/admin/cli/purge_caches.php

