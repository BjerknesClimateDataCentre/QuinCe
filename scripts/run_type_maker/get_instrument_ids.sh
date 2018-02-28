#!/bin/bash
mysql -u $1 --password=$2 $3 -A -e"SELECT u.firstname, u.surname, i.name, i.id, f.description, f.id FROM user u INNER JOIN instrument i ON i.owner = u.id INNER JOIN file_definition f ON f.instrument_id = i.id;"
