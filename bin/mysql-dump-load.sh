#!/bin/bash

db_name=$1
dump_path=$2

#echo . 
#echo running...
#echo docker exec -i fzl-mysql mysql -uroot -p1234 -e "CREATE DATABASE IF NOT EXISTS $db_name;"
#docker exec -i fzl-mysql mysql -uroot -p1234 -e "CREATE DATABASE IF NOT EXISTS $db_name;"

echo .
echo running...
echo "docker exec -i fzl-mysql mysql -uroot -p1234 $db_name < $dump_path"
docker exec -i fzl-mysql mysql -uroot -p1234 $db_name < $dump_path
