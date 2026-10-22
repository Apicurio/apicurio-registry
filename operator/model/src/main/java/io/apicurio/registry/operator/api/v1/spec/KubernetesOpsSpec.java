package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "registryId", "namespace", "refreshEvery", "labelRegistryId", "watchEnabled",
        "watchReconnectDelay" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class KubernetesOpsSpec {

    @JsonProperty("registryId")
    @JsonPropertyDescription("""
            Unique identifier for this registry instance. Only ConfigMaps with a matching label will be loaded.

            Required if `app.storage.type` is `kubernetesops`.""")
    @JsonSetter(nulls = SKIP)
    private String registryId;

    @JsonProperty("namespace")
    @JsonPropertyDescription("""
            Kubernetes namespace to watch for ConfigMaps. Defaults to the namespace of the registry deployment.""")
    @JsonSetter(nulls = SKIP)
    private String namespace;

    @JsonProperty("refreshEvery")
    @JsonPropertyDescription("""
            How often to poll for ConfigMap changes. Supports duration format (e.g., `10s`, `1m`). Defaults to `30s`.""")
    @JsonSetter(nulls = SKIP)
    private String refreshEvery;

    @JsonProperty("labelRegistryId")
    @JsonPropertyDescription("""
            Label key used to identify ConfigMaps belonging to this registry. Defaults to `apicurio.io/registry-id`.""")
    @JsonSetter(nulls = SKIP)
    private String labelRegistryId;

    @JsonProperty("watchEnabled")
    @JsonPropertyDescription("""
            Enable Watch API for real-time ConfigMap change detection. Set to `false` to use polling only. \
            Defaults to `true`.""")
    @JsonSetter(nulls = SKIP)
    private Boolean watchEnabled;

    @JsonProperty("watchReconnectDelay")
    @JsonPropertyDescription("""
            Base delay before reconnecting after watch failure. Uses exponential backoff up to 5 minutes. \
            Defaults to `10s`.""")
    @JsonSetter(nulls = SKIP)
    private String watchReconnectDelay;
}
