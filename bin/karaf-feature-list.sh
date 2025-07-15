#!/bin/bash
KARAF_CONSOLE="docker exec -it fzl-apache-karaf ./bin/client "

#add camel repo
$KARAF_CONSOLE feature:list
