#!/bin/bash

#Rode esse script ./bin/karaf-console.sh pra que esse source funcione
source ./.env

echo FZL_KARAF_CONTAINER_NAME=$FZL_KARAF_CONTAINER_NAME
KARAF_CONSOLE="docker exec -it $FZL_KARAF_CONTAINER_NAME /opt/karaf/bin/client"

#add camel repo
$KARAF_CONSOLE
