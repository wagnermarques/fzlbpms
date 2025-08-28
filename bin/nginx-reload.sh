#!/bin/bash

_THIS_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $_THIS_PATH/utils.sh

echo .
fzlecho "[nginx-reload.sh]" "Reloading Nginx configuration..."
docker exec fzl-nginx nginx -s reload
fzlecho "[nginx-reload.sh]" "Nginx configuration reloaded."
echo .

