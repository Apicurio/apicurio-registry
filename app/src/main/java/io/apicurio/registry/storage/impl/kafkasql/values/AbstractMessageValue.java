package io.apicurio.registry.storage.impl.kafkasql.values;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all message value classes.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public abstract class AbstractMessageValue implements MessageValue {

    private ActionType action;
}
