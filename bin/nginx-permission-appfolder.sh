#!/bin/bash

# This script sets the appropriate permissions for the Nginx application folder.
# It ensures that the Nginx user has the necessary read and execute permissions.
# Usage: sudo ./nginx-permission-appfolder.sh /path/to/your/appfolder
# Example: sudo ./nginx-permission-appfolder.sh /var/www/myapp
# Make sure to run this script with sudo or as root.
# Note: Adjust the NGINX_USER variable if your Nginx runs under a different user.

APPFOLDER=$1
APPFOLDER_INHOST=$(realpath "$APPFOLDER")
FOLDERNAME=$(basename "$APPFOLDER_INHOST")
APPFOLDER_INCONTAINER="/var/www/html/$FOLDERNAME"


# Function to log messages
LOGFILE="nginx-permission-appfolder.log"
log_message() {
    local MESSAGE=$1
    local TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
    local this_filename=$(basename "$0")

    echo "=> $this_filename | $TIMESTAMP | $MESSAGE" | tee -a "$LOGFILE"
}

echo .
log_message "Starting script..."
log_message "App folder in host: $APPFOLDER_INHOST"
log_message "App folder in container: $APPFOLDER_INCONTAINER"


NGINX_USER="nginx"  # Change this if your Nginx runs under
NGINX_GROUP="nginx" # Change this if your Nginx group is different
NGINX_USER_ID=33  # Common UID for nginx user
NGINX_GROUP_ID=33 # Common GID for nginx group

ERROR_FLAG=0
ERROR_MSG=""
SUCCESS_MSG=""
if [ -z "$APPFOLDER" ]; then
    echo "Usage: sudo $0 /path/to/your/appfolder"
    exit 1
fi



echo .
echo .
log_message "checking... if folder exists in host"
if [ ! -d "$APPFOLDER_INHOST" ]; then
    echo "Error: The specified folder '$APPFOLDER' does not exist."
    exit 1
fi

echo "checking... if folder exists in fzl-nginx container"
docker exec -it fzl-nginx sh -c "ls -la $APPFOLDER_INCONTAINER";
if [ $? -ne 0 ]; then
    echo "Error: The specified folder '$APPFOLDER_INCONTAINER' does not exist in the container."
    exit 1
fi

echo "checking... if folder exist in fzl-php8.3-fpm container"
docker exec -it fzl-php8.3-fpm sh -c "ls -la $APPFOLDER_INCONTAINER";
if [ $? -ne 0 ]; then
    echo "Error: The specified folder '$APPFOLDER_INCONTAINER' does not exist in the php container."
    exit 1
fi




# Change ownership to Nginx user and group inside the container
echo .
echo "Changing ownership in host... to $NGINX_USER_ID:$NGINX_GROUP_ID in host"
chown -R $NGINX_USER_ID:$NGINX_GROUP_ID "$APPFOLDER_INHOST"
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to change ownership of $APPFOLDER_INHOST to $NGINX_USER_ID:$NGINX_GROUP_ID."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully changed ownership of $APPFOLDER_INHOST to $NGINX_USER_ID:$NGINX_GROUP_ID."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi

echo "Changing directory permissions in host... to 755 in host"
find "$APPFOLDER_INHOST" -type f -exec chmod 777 {} \; #644 for files
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to set file permissions to 644 in '$APPFOLDER'."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully set file permissions to 644 in '$APPFOLDER'."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi



echo "Changing ownership in fzl-nginx... to $NGINX_USER:$NGINX_GROUP in fzl-nginx container"
docker exec -it fzl-nginx chown -R $NGINX_USER:$NGINX_GROUP "$APPFOLDER_INCONTAINER"
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to change ownership of $APPFOLDER_INCONTAINER to $NGINX_USER:$NGINX_GROUP."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully changed ownership of $APPFOLDER_INHOST to $NGINX_USER:$NGINX_GROUP."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi


echo "Changing permissions in fzl-nginx... to 777 in fzl-nginx container"
docker exec -it fzl-nginx chmod 777 -R  "$APPFOLDER_INCONTAINER" # temporary 777 for testing
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to change permissions of $APPFOLDER_INCONTAINER to $NGINX_USER:$NGINX_GROUP."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully changed permissions of $APPFOLDER_INHOST to $NGINX_USER:$NGINX_GROUP."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi




echo "Changing ownership... to www-data:www-data in fzl-php8.3-fpm container"
docker exec -it fzl-php8.3-fpm chown -R www-data:www-data "$APPFOLDER_INCONTAINER"
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to set directory ownership to 755 in $APPFOLDER_INCONTAINER."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully set directory ownership to 755 in '$APPFOLDER_INCONTAINER'."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi


echo "Changing permissions... to 777 in fzl-php8.3-fpm container"
docker exec -it fzl-php8.3-fpm bash -c "chmod 777 -R $APPFOLDER_INCONTAINER" # temporary 777 for testing
if [ $? -ne 0 ]; then
    ERROR_FLAG=1
    ERROR_MSG="Failed to set directory permissions to 755 in $APPFOLDER_INCONTAINER."
    log_message "$ERROR_MSG"
    echo "Error: $ERROR_MSG"
else
    SUCCESS_MSG="Successfully set directory permissions to 755 in $APPFOLDER_INCONTAINER."
    log_message "$SUCCESS_MSG"
    echo "Success: $SUCCESS_MSG"
fi




# Set execute permissions for scripts (if any)
#find "$APPFOLDER" -type f -name "*.sh" -exec chmod +x
#{} \;
#if [ $? -ne 0 ]; then
#    ERROR_FLAG=1
#    ERROR_MSG="Failed to set execute permissions for scripts in '$APPFOLDER'."
#    log_message "$ERROR_MSG"
#else
#    SUCCESS_MSG="Successfully set execute permissions for scripts in '$APPFOLDER'."
#    log_message "$SUCCESS_MSG"
#fi

#if [ $ERROR_FLAG -eq 1 ]; then
#    echo "Completed with errors. Check the log file at $LOGFILE for details."
#    exit 1
#else
#    echo "Permissions successfully set for '$APPFOLDER'."
#    exit 0
#fi
    
# End of script
