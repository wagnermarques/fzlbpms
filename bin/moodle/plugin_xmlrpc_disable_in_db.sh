#!/bin/bash

#Disable XML-RPC at database level
sql1="INSERT INTO mdl_config (name, value) VALUES ('xmlrpc_enabled', '0') ON DUPLICATE KEY UPDATE value = '0';"
sql2="INSERT INTO mdl_config (name, value) VALUES ('mnet_dispatcher_mode', 'off') ON DUPLICATE KEY UPDATE value = 'off';"

#Disable webservice protocols
#INSERT INTO mdl_config_plugins (plugin, name, value) VALUES ('webservice_xmlrpc', 'enabled', '0') ON DUPLICATE KEY UPDATE value = '0';

#Force skip environment checks
#INSERT INTO mdl_config (name, value) VALUES ('disableupdatenotifications', '1') ON DUPLICATE KEY UPDATE value = '1';
