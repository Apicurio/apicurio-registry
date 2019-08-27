package io.apicurio.registry.kafka;

import io.apicurio.registry.kafka.proto.Reg;
import io.apicurio.registry.kafka.utils.AsyncProducer;
import io.apicurio.registry.kafka.utils.KafkaProperties;
import io.apicurio.registry.kafka.utils.ProtoSerde;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaProducerConfiguration {

    public static final String SCHEMA_TOPIC = "schema-topic";

    @Produces
    @ApplicationScoped // OK?
    public Properties properties(InjectionPoint ip) {
        KafkaProperties kp = ip.getAnnotated().getAnnotation(KafkaProperties.class);
        String prefix = (kp != null ? kp.value() : "");
        Properties properties = new Properties();
        Config config = ConfigProvider.getConfig();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                properties.put(key.substring(prefix.length()), config.getValue(key, String.class));
            }
        }
        return properties;
    }

    @Produces
    @ApplicationScoped
    public Function<ProducerRecord<String, Reg.SchemaValue>, CompletableFuture<RecordMetadata>> schemaProducer(
        @KafkaProperties("registry.kafka.schema-producer.") Properties properties) {
        return new AsyncProducer<>(
            properties,
            new StringSerializer(),
            ProtoSerde.parsedWith(Reg.SchemaValue.parser())
        );
    }
}
