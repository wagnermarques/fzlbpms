#!/bin/bash

# NOTE: http, webconsole, hawtio and the camel features are BOOT features now
# (custom_karaf_etc/etc-from-4.4.7/org.apache.karaf.features.cfg) — Karaf
# installs them on every start. This script is only for trying an extra
# feature by hand before making it permanent there.
#
# The credential is read from the container's own environment (FZL_KARAF_USER /
# FZL_KARAF_PASSWORD, passed from .env by docker-compose.yml), so no password
# is written here. -t is required: without a TTY bin/client swallows all output.

karaf_run() {
    docker exec -t fzl-karaf-camel-integration sh -c \
        "/opt/karaf/bin/client -u \"\$FZL_KARAF_USER\" -p \"\$FZL_KARAF_PASSWORD\" '$1'"
}
karaf_run feature:install http
karaf_run feature:install webconsole
karaf_run feature:install camel
karaf_run feature:install camel-core
karaf_run feature:install camel-blueprint
karaf_run feature:install camel-spring
karaf_run feature:install camel-activemq
karaf_run feature:install camel-exec

#camel-git                         
#camel-github                     
#camel-jdbc


#camel-jetty
#camel-jmx
#camel-jpa
#camel-jsonb
#camel-jsonpath
#camel-ldap    
#camel-ldif
#camel-mongodb
#camel-pdf
#camel-quartz
#camel-service 
#camel-servicenow
#camel-servlet   
#camel-spring-batch
#camel-spring-jdbc 
#camel-spring-ldap 
#camel-spring-ws   
#camel-sql         
#camel-ssh         
#camel-stax        
#camel-stream      
#camel-stomp                 
#camel-test                 
#camel-test-karaf          
#camel-test-spring        
#camel-thrift            
#camel-tracing          
#camel-twilio          
#camel-twitter        
#camel-vertx         
#camel-velocity                  
#camel-weather                  
#camel-websocket               
#camel-websocket-jsr356       
#camel-web3j                 
#camel-webhook              
#camel-wordpress 
