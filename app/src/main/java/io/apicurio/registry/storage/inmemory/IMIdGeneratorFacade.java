package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.store.IdGenerator;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static io.apicurio.registry.storage.CounterStorage.DEFAULT_ID;

@ApplicationScoped
@InMemory
public class IMIdGeneratorFacade implements IdGenerator {

    @Inject
    @InMemory
    private IMCounterStorage counterStorage;

    @Override
    public int nextId() {
        return (int) counterStorage.incrementAndGet(DEFAULT_ID);
    }
}
