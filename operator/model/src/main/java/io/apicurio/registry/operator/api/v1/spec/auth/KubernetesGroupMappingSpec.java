package io.apicurio.registry.operator.api.v1.spec.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"adminGroups", "developerGroups", "readOnlyGroups"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class KubernetesGroupMappingSpec {

    @JsonProperty("adminGroups")
    @JsonPropertyDescription("""
            Comma-separated Kubernetes groups that map to the sr-admin role.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String adminGroups;

    @JsonProperty("developerGroups")
    @JsonPropertyDescription("""
            Comma-separated Kubernetes groups that map to the sr-developer role.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String developerGroups;

    @JsonProperty("readOnlyGroups")
    @JsonPropertyDescription("""
            Comma-separated Kubernetes groups that map to the sr-readonly role.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String readOnlyGroups;
}
