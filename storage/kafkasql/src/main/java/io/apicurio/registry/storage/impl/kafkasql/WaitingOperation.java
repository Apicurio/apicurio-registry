package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Getter
class WaitingOperation {

    private final UUID uuid;
    private final CountDownLatch latch;
    private final Instant createdAt; // Protect against leaks

    private final MessageKey key;

    /**
     * Might be null.
     */
    private final ActionType actionType;

    @Setter
    private volatile Object returnValue;

    public WaitingOperation(MessageKey key, ActionType actionType) {
        uuid = UUID.randomUUID();
        latch = new CountDownLatch(1);
        createdAt = Instant.now();

        this.key = key;
        this.actionType = actionType;
    }
}
