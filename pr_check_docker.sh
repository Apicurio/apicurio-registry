#!/bin/bash -ex

# start postgres
which pg_ctl
PGDATA=/var/lib/postgresql/data /usr/lib/postgresql/*/bin/pg_ctl -w stop
PGDATA=/var/lib/postgresql/data /usr/lib/postgresql/*/bin/pg_ctl start -o "-c listen_addresses='*' -p 5432"

echo "Running make pr-check"
make pr-check

# required for entrypoint script run by docker to exit and stop container
exit 0