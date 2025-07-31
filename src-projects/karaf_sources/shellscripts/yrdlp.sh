#!/bin/bash
URL=$1
python3 -m yt_dlp $URL --output '/dir_output/%(title)s.%(ext)s'
