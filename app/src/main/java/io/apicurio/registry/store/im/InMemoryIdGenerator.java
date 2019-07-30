package io.apicurio.registry.store.im;

import io.apicurio.registry.store.IdGenerator;

import java.util.concurrent.atomic.AtomicInteger;
import javax.enterprise.context.ApplicationScoped;

/**
 * Testing / development only!
 *
 * @author Ales Justin
 */
@ApplicationScoped
public class InMemoryIdGenerator implements IdGenerator {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public int nextId() {
        return counter.incrementAndGet();
    }
}
