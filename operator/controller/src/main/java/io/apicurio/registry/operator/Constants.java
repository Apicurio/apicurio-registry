package io.apicurio.registry.operator;

import io.apicur.registry.v1.ApicurioRegistry;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.QuantityBuilder;
import java.util.HashMap;
import java.util.Map;

public class Constants {

  public static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
  public static final String MANAGED_BY_VALUE = "apicurio-registry-operator";
  public static final String LABEL_SELECTOR_KEY = "app.apicurio-registry-operator.io/managed";
  public static final String LABEL_SELECTOR_VALUE = "true";

  public static final int DEFAULT_REPLICAS = 1;
  public static final String CONTAINER_NAME = "registry";
  public static final String DEFAULT_CONTAINER_IMAGE =
      "apicurio/apicurio-registry-mem:latest-snapshot";

  public static final Map<String, Quantity> DEFAULT_REQUESTS =
      Map.of(
          "cpu", new QuantityBuilder().withAmount("500").withFormat("m").build(),
          "memory", new QuantityBuilder().withAmount("512").withFormat("Mi").build());
  public static final Map<String, Quantity> DEFAULT_LIMITS =
      Map.of(
          "cpu", new QuantityBuilder().withAmount("1").build(),
          "memory", new QuantityBuilder().withAmount("1300").withFormat("Mi").build());
  public static final Probe DEFAULT_READINESS_PROBE =
      new ProbeBuilder()
          .withNewHttpGet()
          .withPath("/health/ready")
          .withNewPort()
          .withValue(8080)
          .endPort()
          .endHttpGet()
          .withInitialDelaySeconds(15)
          .withTimeoutSeconds(5)
          .withPeriodSeconds(10)
          .withSuccessThreshold(1)
          .withFailureThreshold(3)
          .build();

  public static final Probe DEFAULT_LIVENESS_PROBE =
      new ProbeBuilder()
          .withNewHttpGet()
          .withPath("/health/live")
          .withNewPort()
          .withValue(8080)
          .endPort()
          .endHttpGet()
          .withInitialDelaySeconds(15)
          .withTimeoutSeconds(5)
          .withPeriodSeconds(10)
          .withSuccessThreshold(1)
          .withFailureThreshold(3)
          .build();

  public static final Map<String, String> BASIC_LABELS =
      Map.of(
          MANAGED_BY_LABEL, MANAGED_BY_VALUE,
          LABEL_SELECTOR_KEY, LABEL_SELECTOR_VALUE);

  public static final Map<String, String> defaultLabels(ApicurioRegistry apicurioRegistry) {
    var labels = new HashMap();
    labels.putAll(BASIC_LABELS);
    labels.put("app", apicurioRegistry.getMetadata().getName());
    return labels;
  }
}
