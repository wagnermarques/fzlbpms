#!/bin/bash

_THIS_DIR=$(dirname "$(readlink -f "$0")")

echo "bin/yii2apps/install.sh running..."

source "$_THIS_DIR/../utils.sh"
$ECHOPREFIX="bin/yii2apps/install.sh"
fzlecho $ECHOPREFIX "Installing Yii2 app..."

APP_GIT_REMOTE_URL=https://github.com/wagnermarques/catracas-secretaria-yii2.git
APP_CONTEXT_NAME=catracas-secretaria-yii2

VAR_WWW_DIR=$_THIS_DIR/../../src-projects/var_www/html

fzlecho $ECHOPREFIX "Cloning Yii2 app from $APP_GIT_REMOTE_URL..."
git clone $APP_GIT_REMOTE_URL "$VAR_WWW_DIR/$APP_CONTEXT_NAME"

if [ $? -ne 0 ]; then
    fzlecho $ECHOPREFIX "Failed to clone Yii2 app repository."
    #exit 1
fi

fzlecho $ECHOPREFIX "Cloning completed successfully."



fzlecho $ECHOPREFIX "Copy yii2app-nginx-configuration.conf file to nginx-conf.d of container fzl-nginx..."
docker cp "$_THIS_DIR/yii2app-nginx-configuration.conf" fzl-nginx:/etc/nginx/conf.d/$APP_CONTEXT_NAME.conf
if [ $? -ne 0 ]; then
    fzlecho $ECHOPREFIX "Failed to copy nginx configuration file."
    #exit 1
fi
fzlecho $ECHOPREFIX "Setting permissions fornginx configuration file in fzl-nginx container..."
docker exec -it fzl-nginx chown root:root /etc/nginx/conf.d/$APP_CONTEXT_NAME.conf
docker exec -it fzl-nginx chmod 644 /etc/nginx/conf.d/$APP_CONTEXT_NAME.conf

#restart nginx service
fzlecho $ECHOPREFIX "Nginx configuration file copied successfully."
fzlecho $ECHOPREFIX "Restarting fzl-nginx container to apply new configuration..."
docker exec -it fzl-nginx nginx -s reload



fzlecho $ECHOPREFIX "Setting up permissions for Yii2 app..."
fzlecho $ECHOPREFIX "Using context name: $APP_CONTEXT_NAME in fzl-nginx container"
docker exec -it fzl-nginx chown www-data:www-data -R /var/www/html

fzlecho $ECHOPREFIX "Setting ownership and permissions for Yii2 app in fzl-php8.3-fpm container..."
docker exec -it fzl-php8.3-fpm chown www-data:www-data -R /var/www/html

chmod 777 -R $VAR_WWW_DIR/$APP_CONTEXT_NAME


#docker exec -it fzl-nginx chown -R www-data:www-data "$APP_CONTEXT_NAME"
#docker exec -it fzl-nginx chmod -R 775 "$APP_CONTEXT_NAME"
#docker exec -it fzl-php8.3-fpm chmod -R 777 "$APP_CONTEXT_NAME/runtime"

fzlecho $ECHOPREFIX "Setting up Yii2 app..."
