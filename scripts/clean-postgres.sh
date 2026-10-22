#!/bin/bash

PGPASSWORD=test psql -h localhost -p 5432 -U test -d test -a -f scripts/clean-postgres.sql