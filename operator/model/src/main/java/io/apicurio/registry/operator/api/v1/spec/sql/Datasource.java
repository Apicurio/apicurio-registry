package io.apicurio.registry.operator.api.v1.spec.sql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "url", "username", "password" })
@JsonDeserialize(using = JsonDeserializer.None.class)
@Getter
@Setter
@ToString
public class Datasource {

    @JsonProperty("url")
    @JsonPropertyDescription("""
            Data source URL: \\n URL of the PostgreSQL
            database, for example: `jdbc:postgresql://<service name>.<namespace>.svc:5432/<database name>`.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String url;

    @JsonProperty("username")
    @JsonPropertyDescription("""
            Data source username.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String username;

    @JsonProperty("password")
    @JsonPropertyDescription("""
            Data source password.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String password;

    // TODO: support values from Secrets:
    //
    // Proposal to support secrets, add alternative fields like:
    // @JsonProperty("passwordFrom")
    // @JsonPropertyDescription("""
    // Data source password from Secret/ConfigMap/...""")
    // @JsonSetter(nulls = Nulls.SKIP)
    // private EnvVarSource passwordFrom;

}
