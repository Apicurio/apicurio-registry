#!/bin/bash

# DB_VERSION_BUILD=$(yq .project.properties."apicurio.sql.storage.db-version" app/pom.xml -r)
DB_VERSION_BUILD=$(cat app/src/main/resources/io/apicurio/registry/storage/impl/sql/db-version)
echo "Build's DB version is $DB_VERSION_BUILD"

DDLS="app/src/main/resources/io/apicurio/registry/storage/impl/sql/postgresql.ddl app/src/main/resources/io/apicurio/registry/storage/impl/sql/h2.ddl"
for ddl in $DDLS 
do
    echo "Processing DDL $ddl"
    DB_VERSION_INSERT=$(grep "INSERT INTO apicurio (propName, propValue) VALUES ('db_version'" $ddl)
    DB_VERSION_IN_DDL=$(echo $DB_VERSION_INSERT | awk '{ print $8 }' - | awk -F ")" '{ print $1}' -)
    echo "DB version in DDL is $DB_VERSION_IN_DDL"

    if (( $(echo "$DB_VERSION_BUILD $DB_VERSION_IN_DDL" | awk '{print ($1 != $2)}') )); then
        echo "DB version mismatch between DDL and build"
        exit 1
    fi
done
echo "DB version ok between build and DDLs"
