## Functions

setupKafka() {
    if [ -z $KAFKA_HOME ]; then
      echo 'Missing KAFKA_HOME env var'
      exit 1;
    fi
    trap "exit 1" SIGINT SIGTERM
    # ZooKeeper & Kafka
    cleanup
    echo $KAFKA_HOME
    $KAFKA_HOME/bin/zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties &
    sleep 1
    $KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_HOME/config/server.properties &
    sleep 2
    echo 'Kafka ready ...'
}

cleanup() {
    ps -ax | grep kafka | awk '{print $1}' | xargs kill -9
    ps -ax | grep zookeeper | awk '{print $1}' | xargs kill -9
    rm -rf /tmp/zookeeper/
    rm -rf /tmp/kafka-*
}

## Main

if [ -z $1 ]; then
    echo 'Setting-up Kafka ...'
    setupKafka
elif [ "$1" == "cleanup" ]
then
    echo 'Kafka cleanup ...'
    cleanup
fi
