#!/bin/bash

# in development only

# Set the project name for consistency, though 'docker-compose'
# usually derives it from the directory name.
# export COMPOSE_PROJECT_NAME=fzl

# --- Pre-startup Permissions Setup ---

# The *-data dirs need to be authorized for the softwares to write in them.
# This command grants full permissions (777) recursively to all directories 
# named with the suffix '-data' inside the 'containers' directory.

#echo " ==> Setting permissions (chmod 777 -R) on all *-data directories..."

# Find all directories ending with '-data' in 'containers' and set permissions
# Show what directories are being modified for transparency
#for d in $(find ./containers/ -type d -iname "*-data"); do
#    echo "Setting permissions for directory: $d"
#    sudo chmod 777 -R "$d"
#done


# Additionally, set permissions for 'src-projects/var_www/' directory
#sudo chmod 777 -R src-projects/var_www/

#echo " ==> Permissions set."



echo " ==> --- Docker Compose Execution ---"

# Check if any container names were passed as arguments
if [ "$#" -eq 0 ]; then
    echo "No container names provided. Starting ALL services defined in docker-compose.yml."
    # If no parameters, start all services in detached mode
    docker-compose up -d
else
    echo "Starting specified service(s): $*"
    # If parameters are provided, pass them to 'docker-compose up -d'
    # The "$@" expands to all arguments received by the script.
    docker-compose up -d "$@"
fi

echo "Startup complete. Run 'docker-compose ps' to check status."
