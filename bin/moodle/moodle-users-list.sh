
#!/bin/bash

docker exec -it fzl-php8.3-fpm php -r '
define("CLI_SCRIPT", true);
require "/var/www/html/moodle/config.php";
$users = $DB->get_records("user", null, "id ASC", "id,username,email,firstname,lastname,deleted,suspended");
foreach ($users as $u) {
    echo "{$u->id}\t{$u->username}\t{$u->email}\t{$u->firstname} {$u->lastname}\tdeleted={$u->deleted}\tsuspended={$u->suspended}\n";
}'

