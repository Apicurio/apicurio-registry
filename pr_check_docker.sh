#!/bin/bash -ex

# base image docker.io/postgres:12
# copied db setup commands from https://github.com/docker-library/postgres/blob/master/12/alpine/docker-entrypoint.sh
source docker-entrypoint.sh
docker_setup_env
docker_create_db_directories
docker_verify_minimum_env
ls /docker-entrypoint-initdb.d/ > /dev/null
docker_init_database_dir
pg_setup_hba_conf
export PGPASSWORD="${PGPASSWORD:-$POSTGRES_PASSWORD}"

# start postgres
which pg_ctl
PGDATA=/var/lib/postgresql/data /usr/lib/postgresql/*/bin/pg_ctl start -o "-c listen_addresses='*' -p 5432"

docker_setup_db

echo "Running make pr-check"
make pr-check

# no need to stop the database
# PGDATA=/var/lib/postgresql/data /usr/lib/postgresql/*/bin/pg_ctl -w stop

# required for entrypoint script run by docker to exit and stop container
exit 0