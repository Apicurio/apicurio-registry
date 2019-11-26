## Functions

setupStreams() {
    if [ -z $KAFKA_HOME ]; then
      echo 'Missing KAFKA_HOME env var'
      exit 1;
    fi
    trap "exit 1" SIGINT SIGTERM
    # ZooKeeper & Kafka
    cleanup
    echo $KAFKA_HOME
    cd $KAFKA_HOME/bin
    ./zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties &
    sleep 1
    ./kafka-server-start.sh $KAFKA_HOME/config/server.properties &
    sleep 2
    ./kafka-topics.sh --zookeeper localhost --create --topic storage-topic --partitions 1 --replication-factor 1  --config cleanup.policy=compact
    ./kafka-topics.sh --zookeeper localhost --create --topic global-id-topic --partitions 1 --replication-factor 1 --config cleanup.policy=compact
    ./kafka-topics.sh --zookeeper localhost --create --topic search-configs --partitions 1 --replication-factor 1 --config cleanup.policy=compact
    ./kafka-topics.sh --zookeeper localhost --create --topic search-offsets --partitions 1 --replication-factor 1 --config cleanup.policy=compact
    ./kafka-topics.sh --zookeeper localhost --create --topic search-status --partitions 1 --replication-factor 1 --config cleanup.policy=compact
    ./kafka-topics.sh --zookeeper localhost --create --topic search-topic --partitions 1 --replication-factor 1
    sleep 1
    echo 'Streams ready ...'
}

cleanup() {
    ps -ax | grep kafka | awk '{print $1}' | xargs kill -9
    ps -ax | grep zookeeper | awk '{print $1}' | xargs kill -9
    rm -rf /tmp/zookeeper/
    rm -rf /tmp/kafka-*
}

## Main

if [ -z $1 ]; then
    echo 'Setting-up Streams ...'
    setupStreams
elif [ "$1" == "cleanup" ]
then
    echo 'Streams cleanup ...'
    cleanup
fi
