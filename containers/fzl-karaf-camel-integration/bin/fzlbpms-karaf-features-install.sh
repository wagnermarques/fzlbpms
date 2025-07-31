#!/bin/bash

# it's requires that karaf container is running with fzl-apache-karaf container name
# change to this script dir to runs it

KARAF_CONSOLE="docker exec -it fzl-apache-karaf /opt/karaf/bin/client -u karaf -p karaf "system:property -p admin.password Admin123""
$KARAF_CONSOLE feature:install http
$KARAF_CONSOLE feature:install webconsole
$KARAF_CONSOLE feature:install camel
$KARAF_CONSOLE feature:install camel-core
$KARAF_CONSOLE feature:install camel-blueprint
$KARAF_CONSOLE feature:install camel-spring
$KARAF_CONSOLE feature:install camel-activemq
$KARAF_CONSOLE feature:install camel-exec

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
