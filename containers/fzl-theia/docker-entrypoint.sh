#!/bin/bash
set -euo pipefail

# /opt/theia-reference (Theia's own source, see Dockerfile) can't be placed
# directly under /home/workspace in the image: ./workspaces is bind-mounted
# over /home/workspace at container start and would hide it. Symlink it in
# fresh each start instead, so it's browsable from inside the running IDE.
if [ ! -e /home/workspace/theia-source ]; then
    ln -s /opt/theia-reference /home/workspace/theia-source
fi

exec "$@"
