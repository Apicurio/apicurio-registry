package io.apicurio.registry.systemtest.messaginginfra.resources;

public interface PersistenceKind {
    String MEM = "mem";
    String SQL = "sql";
    String KAFKA_SQL = "kafkasql";
}
