#!/bin/bash
# in development only

# Set the project name for consistency, though 'docker-compose'
# usually derives it from the directory name.
# export COMPOSE_PROJECT_NAME=fzl

# --- Pre-startup Permissions Setup ---

# The *-data dirs need to be authorized for the softwares to write in them.
# This command grants full permissions (777) recursively to all directories 
# named with the suffix '-data' inside the 'containers' directory.
echo "Setting permissions (chmod 777 -R) on all *-data directories..."
sudo find ./containers/ -type d -iname "*-data" -print0 | xargs -0 chmod 777 -R
echo "Permissions set."

# --- Docker Compose Execution ---

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
