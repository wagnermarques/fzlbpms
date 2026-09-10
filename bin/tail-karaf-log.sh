#!/bin/bash

# containers/fzl-karaf-camel-integration/karaf-data/ is bind-mounted to
# /opt/karaf/data in the container.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tail -f "$SCRIPT_DIR/../containers/fzl-karaf-camel-integration/karaf-data/log/karaf.log"

