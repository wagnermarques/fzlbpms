#!/bin/bash

KARAF_CONSOLE="docker compose exec -it $FZL_KARAF_CONTAINER_NAME ./bin/client "

#add camel repo
$KARAF_CONSOLE feature:list
