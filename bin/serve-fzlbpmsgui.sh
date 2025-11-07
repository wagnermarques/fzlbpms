#!/bin/bash

# This script starts a simple web server to serve the fzlbpmsgui directory
# and opens the default browser to the index.html page.

GUI_DIR="/run/media/wgn/ext4/Projects-Srcs/fzlbpms/src-projects/fzlbpmsgui"
PORT=8085
URL="http://localhost:$PORT"

echo "Changing directory to $GUI_DIR"
cd "$GUI_DIR" || exit

echo "Starting Python 3 web server on port $PORT"
python3 -m http.server $PORT &
SERVER_PID=$!

# Give the server a moment to start up
sleep 1

echo "Opening $URL in your default browser."
xdg-open "$URL"

echo "Server is running in the background with PID: $SERVER_PID"
echo "To stop the server, run: kill $SERVER_PID"
