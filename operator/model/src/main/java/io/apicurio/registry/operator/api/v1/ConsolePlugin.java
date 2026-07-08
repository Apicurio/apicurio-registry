package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Minimal typed representation of the OpenShift ConsolePlugin CR.
 * Used by the operator to create/manage the ConsolePlugin resource
 * without depending on the OpenShift client library.
 * <p>
 * Note: ConsolePlugin is cluster-scoped, but we implement Namespaced
 * because JOSDK requires it for dependent resources managed by a
 * namespaced primary. The resource itself has no namespace set.
 */
@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@Group("console.openshift.io")
@Version("v1")
@Kind("ConsolePlugin")
@Plural("consoleplugins")
public class ConsolePlugin extends CustomResource<Void, Void> implements Namespaced {

    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        additionalProperties.put(key, value);
    }
}
