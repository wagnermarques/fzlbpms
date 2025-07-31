#!/bin/bash

#runs ansible-playbook playbook from any directory
BUILD_CONTEXT_DIR="/media/wgn/EnvsBk1/__devenv__/fzlbpms/gitsubmodules/fzl-integration-karaf-camel-routes/build"

cd $BUILD_CONTEXT_DIR
ansible-playbook playbook.yml
