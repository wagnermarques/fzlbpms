#!/bin/bash

docker exec -it fzl-nginx  tail -f /var/log/nginx/error.log