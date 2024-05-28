#!/bin/bash

DONE=false

curl -s http://localhost:8080/apis/registry/v2/groups/Examples/artifacts/MessageType > _schema.json
echo "Schema fetched from registry."
cat _schema.json

echo ""
echo "---"
echo "Subscribing to broker."
echo "---"


while [ "x$DONE" == "xfalse" ]
do

    # Receive a message
    curl http://localhost:12345 -s > _message.json
    
    MESSAGE_SIZE=$(wc -c "_message.json" | awk '{print $1}')
    
    if [ "x$MESSAGE_SIZE" == "x0" ]
    then
        continue
    else
        #json validate --schema-file=_schema.json --document-file=_message.json
        echo "Message received and validated."
        cat _message.json | jq
    fi
    
    sleep 0.2

done
