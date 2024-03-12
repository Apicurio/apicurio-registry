package io.apicurio.example.debezium.rest;

import io.apicurio.example.debezium.model.Product;
import io.apicurio.example.debezium.sql.Database;
import io.quarkus.scheduler.Scheduled;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Random;
import java.util.UUID;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class ExampleRunner {

    private static final Random RANDOM = new Random();

    private static final Logger log = LoggerFactory.getLogger(ExampleRunner.class);


    @Getter
    @Setter
    private boolean isEnabled;

    @Inject
    Database database;


    @Scheduled(every = "5s", concurrentExecution = SKIP)
    public void run() {
        if (isEnabled) {
            var product = Product.builder()
                    .name("name-" + UUID.randomUUID())
                    .description("description-" + UUID.randomUUID())
                    .weight(RANDOM.nextFloat() * 100 + 1)
                    .build();
            log.info("Inserting: {}", product);
            product.setId(database.insertProduct(product));
            product.setName("updated-" + product.getName());
            log.info("Updating: {}", product);
            database.updateProduct(product);
            log.info("Deleting: {}", product);
            database.deleteProduct(product);
        }
    }
}
