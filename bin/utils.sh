#!/bin/bash

echo .
echo " => utils.sh loaded."


function fzlecho() {
    PREFIX=$1
    SEP=" | "
    MSG=$2    
    echo -e " #### \033[1;32m$PREFIX$SEP$MSG\033[0m"
}


