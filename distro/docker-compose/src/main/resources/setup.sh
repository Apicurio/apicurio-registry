#!/bin/bash

if [ -z "$1" ]
then
  echo "Please provide the neccessary arguments!"
  exit 1
fi

HOST_IP=$1
P=$(pwd)

##if the script runs in the container, we have to adjust the path to the mount point
if [ $P == "/" ]
then
  export P=/apicurio
fi

KC_ROOT_DB_PASSWORD=$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c6)
KC_DB_PASSWORD=$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c6)
KC_PASSWORD=$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c6)
AS_DB_PASSWORD=$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c6)

SERVICE_CLIENT_SECRET=$(uuidgen)

sed 's/$HOST/'"$HOST_IP"'/g' $P/.env.template > $P/tmp; mv $P/tmp $P/.env

sed 's/$KC_ROOT_DB_PASSWORD/'"$KC_ROOT_DB_PASSWORD"'/g' $P/.env > $P/tmp; mv $P/tmp $P/.env
sed 's/$KC_DB_PASSWORD/'"$KC_DB_PASSWORD"'/g' $P/.env > $P/tmp; mv $P/tmp $P/.env
sed 's/$KC_PASSWORD/'"$KC_PASSWORD"'/g' $P/.env > $P/tmp; mv $P/tmp $P/.env
sed 's/$AS_DB_PASSWORD/'"$AS_DB_PASSWORD"'/g' $P/.env > $P/tmp; mv $P/tmp $P/.env
sed 's/$SERVICE_CLIENT_SECRET/'"$SERVICE_CLIENT_SECRET"'/g' $P/.env > $P/tmp; mv $P/tmp $P/.env


echo "Keycloak username: admin"
echo "Keycloak password: $KC_PASSWORD"
echo "Keycloak URL: $HOST_IP:8090"
echo "Apicurio URL: $HOST_IP:8080"