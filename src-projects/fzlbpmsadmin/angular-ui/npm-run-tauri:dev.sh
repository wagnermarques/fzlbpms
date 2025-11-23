#!/bin/bash

# Check if AT-SPI bus file exists for the current user
BUS_FILE="$HOME/.cache/at-spi/bus_0"

if [ -f "$BUS_FILE" ]; then
    echo "Found AT-SPI bus file: $BUS_FILE"
    export AT_SPI_BUS_ADDRESS=$(cat "$BUS_FILE")
    echo "AT_SPI_BUS_ADDRESS set to: $AT_SPI_BUS_ADDRESS"
else
    echo "AT-SPI bus file not found. Starting accessibility bus..."
    /usr/libexec/at-spi-bus-launcher &
    sleep 1
    if [ -f "$BUS_FILE" ]; then
        export AT_SPI_BUS_ADDRESS=$(cat "$BUS_FILE")
        echo "AT_SPI_BUS_ADDRESS set after starting bus."
    else
        echo "Failed to start AT-SPI bus. Accessibility may not work."
    fi
fi

# Run your Tauri dev command
npm run tauri:dev
