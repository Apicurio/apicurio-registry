package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceKey.REGISTRY_KEY;
import static io.apicurio.registry.operator.utils.Mapper.duplicate;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

public class ResourceUtils<D> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);

    private final ApicurioRegistry3 primary;

    private final Context<ApicurioRegistry3> context;

    private final ResourceKey<D> desiredResourceKey;

    private final D desiredResource;

    public ResourceUtils(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
            ResourceKey<D> desiredResourceKey) {
        this.primary = primary;
        this.context = context;
        this.desiredResourceKey = desiredResourceKey;

        MDC.put("cr-context",
                " [%s:%s]".formatted(primary.getMetadata().getNamespace(), primary.getMetadata().getName()));

        log.debug("Reconciling {}", desiredResourceKey);

        if (REGISTRY_KEY.equals(desiredResourceKey)) {
            desiredResource = duplicate((D) primary, desiredResourceKey.getKlass());
        } else {
            desiredResource = desiredResourceKey.getFactory().apply(primary);
        }
    }

    public void close() {
        MDC.remove("cr-context");
    }

    public <R> void withExistingResource(ResourceKey<R> key, Consumer<R> action) {
        if (REGISTRY_KEY.equals(key)) {
            action.accept((R) primary);
        } else {
            var r = context.getSecondaryResource(key.getKlass(), key.getDiscriminator());
            if (r.isPresent()) {
                action.accept(r.get());
            } else {
                log.debug("Existing resource {} not found.", key);
            }
        }
    }

    public void withDesiredResource(Consumer<D> action) {
        action.accept(desiredResource);
    }

    public D returnDesiredResource() {
        log.debug("Desired {} is {}", desiredResourceKey, toYAML(desiredResource));
        return desiredResource;
    }
}
