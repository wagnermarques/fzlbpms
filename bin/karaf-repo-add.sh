#!/bin/bash
KARAF_CONSOLE="docker exec -it fzl-apache-karaf ./bin/client "

#add camel repo
$KARAF_CONSOLE repo-add mvn:org.apache.camel.karaf/apache-camel/3.21.0/xml/features
