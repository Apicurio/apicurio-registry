package io.apicurio.registry.storage;

import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class RegistryStorageProducerTest {

    private RegistryStorageProducer producer;
    private SqlRegistryStorage sqlRegistryStorageMock;
    private KafkaSqlRegistryStorage kafkaSqlRegistryStorageMock;

    @BeforeEach
    public void setup() {
        producer = new RegistryStorageProducer();
        producer.log = Mockito.mock(org.slf4j.Logger.class);

        sqlRegistryStorageMock = Mockito.mock(SqlRegistryStorage.class);
        Instance<SqlRegistryStorage> sqlInstance = Mockito.mock(Instance.class);
        when(sqlInstance.get()).thenReturn(sqlRegistryStorageMock);
        producer.sqlRegistryStorage = sqlInstance;

        kafkaSqlRegistryStorageMock = Mockito.mock(KafkaSqlRegistryStorage.class);
        Instance<KafkaSqlRegistryStorage> kafkaSqlInstance = Mockito.mock(Instance.class);
        when(kafkaSqlInstance.get()).thenReturn(kafkaSqlRegistryStorageMock);
        producer.kafkaSqlRegistryStorage = kafkaSqlInstance;
    }

    @Test
    public void testSqlStorageConnectionFailure() {
        producer.registryStorageType = "sql";
        producer.jdbcUrl = "jdbc:postgresql://invalid-db:5432/registry";
        
        SQLException sqlException = new SQLException("Connection refused");
        RegistryStorageException wrapperException = new RegistryStorageException(sqlException);
        
        doThrow(wrapperException).when(sqlRegistryStorageMock).initialize();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            producer.raw();
        });

        assertEquals("ERROR: PostgreSQL not reachable at jdbc:postgresql://invalid-db:5432/registry. Check that the database is running and the connection URL is correct.", thrown.getMessage());
        assertEquals(wrapperException, thrown.getCause());
    }

    @Test
    public void testKafkaSqlStorageConnectionFailure() {
        producer.registryStorageType = "kafkasql";
        producer.kafkaBootstrapServers = "invalid-broker:9092";
        
        TimeoutException timeoutException = new TimeoutException("Timed out waiting for a node assignment.");
        
        doThrow(timeoutException).when(kafkaSqlRegistryStorageMock).initialize();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            producer.raw();
        });

        assertEquals("ERROR: Kafka not reachable at invalid-broker:9092. Check that Kafka is running and the bootstrap servers are correct.", thrown.getMessage());
        assertEquals(timeoutException, thrown.getCause());
    }

    @Test
    public void testOtherExceptionsAreNotWrapped() {
        producer.registryStorageType = "sql";
        producer.jdbcUrl = "jdbc:postgresql://db:5432/registry";
        
        IllegalArgumentException otherException = new IllegalArgumentException("Some other error");
        doThrow(otherException).when(sqlRegistryStorageMock).initialize();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            producer.raw();
        });

        assertEquals("Some other error", thrown.getMessage());
    }
}
