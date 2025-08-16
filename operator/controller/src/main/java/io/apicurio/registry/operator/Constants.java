package io.apicurio.registry.operator;

import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.QuantityBuilder;

import java.util.Map;

public class Constants {

    public static final int DEFAULT_REPLICAS = 1;

    public static final Map<String, Quantity> DEFAULT_REQUESTS = Map.of("cpu",
            new QuantityBuilder().withAmount("500").withFormat("m").build(), "memory",
            new QuantityBuilder().withAmount("512").withFormat("Mi").build());
    public static final Map<String, Quantity> DEFAULT_LIMITS = Map.of("cpu",
            new QuantityBuilder().withAmount("1").build(), "memory",
            new QuantityBuilder().withAmount("1300").withFormat("Mi").build());

    public static final Probe DEFAULT_READINESS_PROBE = new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/health/ready").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build();
    public static final Probe DEFAULT_LIVENESS_PROBE = new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/health/live").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build();

    public static final Probe TLS_DEFAULT_READINESS_PROBE = new ProbeBuilder().withNewHttpGet()
            .withScheme("HTTPS").withPath("/health/ready").withNewPort().withValue(8443).endPort().endHttpGet()
            .withInitialDelaySeconds(15).withTimeoutSeconds(5).withPeriodSeconds(10).withSuccessThreshold(1)
            .withFailureThreshold(3).build();

    public static final Probe TLS_DEFAULT_LIVENESS_PROBE = new ProbeBuilder().withNewHttpGet()
            .withScheme("HTTPS").withPath("/health/live").withNewPort().withValue(8443).endPort().endHttpGet()
            .withInitialDelaySeconds(15).withTimeoutSeconds(5).withPeriodSeconds(10).withSuccessThreshold(1)
            .withFailureThreshold(3).build();
}
