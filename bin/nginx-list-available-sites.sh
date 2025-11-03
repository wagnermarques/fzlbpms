#!/bin/bash

# Script to list available sites in nginx configuration

# Directory containing nginx configuration files
CONF_DIR="/run/media/wgn/ext4/Projects-Srcs/fzlbpms/containers/fzl-nginx/nginx-conf.d/"

# Check if the directory exists
if [ ! -d "$CONF_DIR" ]; then
  echo "Error: Configuration directory not found at $CONF_DIR"
  exit 1
fi

echo "Available sites:"
echo "----------------"

# Loop through each .conf file in the directory
for conf_file in "$CONF_DIR"*.conf; do
  # Extract server_name and listen port
  server_name=$(grep -oP 'server_name\s+\K[^;]+' "$conf_file" | head -n 1)
  listen_port=$(grep -oP 'listen\s+\K[^;]+' "$conf_file" | head -n 1)

  # Get the file name for the site name
  site_name=$(basename "$conf_file" .conf)

  # Output the site information
  echo "Site: $site_name"
  
  # Construct the URL
  if [ "$listen_port" = "80" ]; then
    url="http://$server_name"
  else
    url="http://$server_name:$listen_port"
  fi
  
  echo "  URL: $url"

  # Extract location blocks
  locations=$(grep -oP 'location\s+[^{]+' "$conf_file" | sed 's/location//' | sed 's/{//' | tr -d ' ' | grep -v "~")
  if [ -n "$locations" ]; then
    echo "  Paths:"
    for location in $locations; do
        echo "    - $url$location"
    done
  fi
  echo ""
done
