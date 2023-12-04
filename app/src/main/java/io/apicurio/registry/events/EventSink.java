package io.apicurio.registry.events;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;


public interface EventSink {

    String name();

    boolean isConfigured();

    void handle(Message<Buffer> message);

}
