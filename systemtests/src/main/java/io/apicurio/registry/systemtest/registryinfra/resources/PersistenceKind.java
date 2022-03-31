package io.apicurio.registry.systemtest.registryinfra.resources;

public interface PersistenceKind {
    String MEM = "mem";
    String SQL = "sql";
    String KAFKA_SQL = "kafkasql";
}
