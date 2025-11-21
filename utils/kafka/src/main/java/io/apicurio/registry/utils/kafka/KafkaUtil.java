package io.apicurio.registry.utils.kafka;

import org.apache.kafka.common.KafkaFuture;

import java.util.concurrent.CompletableFuture;

public final class KafkaUtil {

    public static <T> CompletableFuture<T> toJavaFuture(KafkaFuture<T> kf) {
        var cf = new CompletableFuture<T>();
        kf.whenComplete((v, t) -> {
            if (t != null) {
                cf.completeExceptionally(t);
            } else {
                cf.complete(v);
            }
        });
        return cf;
    }

    private KafkaUtil() {
    }
}
