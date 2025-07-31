#!/bin/bash
dest="$1_(date+%y%m%d%H%M%S)"
sudo rsync -va  "./../../karaf_data"  $dest

