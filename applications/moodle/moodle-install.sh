#!/bin/bash

source ./.env

echo FZL_HOME=$FZL_HOME

cd "$FZL_HOME/src-projects/var_www_html/"

git clone https://github.com/moodle/moodle.git

#cd ./moodle

#git checkout -b MOODLE_500_STABLE origin/MOODLE_500_STABLE 

ls -la
