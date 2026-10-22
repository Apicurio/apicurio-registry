package io.apicurio.registry.operator.api.v1.spec;

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

/**
 * Configuration for Elasticsearch-based search indexing in Apicurio Registry.
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "hosts", "indexName", "username", "password"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchIndexSpec {

    /**
     * Enable Elasticsearch-based search indexing for Apicurio Registry.
     * When enabled, Registry indexes artifact content in Elasticsearch to support
     * full-text content and structure searches. Default is false.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable Elasticsearch-based search indexing for Apicurio Registry.
            When enabled, Registry indexes artifact content in Elasticsearch to support
            full-text content and structure searches. Default is false.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    /**
     * The Elasticsearch hosts to connect to.
     * Example: elasticsearch:9200
     */
    @JsonProperty("hosts")
    @JsonPropertyDescription("""
            The Elasticsearch hosts to connect to.
            Example: elasticsearch:9200""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String hosts;

    /**
     * The name of the Elasticsearch index to use.
     * If not specified, defaults to 'apicurio-registry' in the application.
     */
    @JsonProperty("indexName")
    @JsonPropertyDescription("""
            The name of the Elasticsearch index to use.
            If not specified, defaults to 'apicurio-registry' in the application.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String indexName;

    /**
     * The username for connecting to a secured Elasticsearch cluster.
     */
    @JsonProperty("username")
    @JsonPropertyDescription("""
            The username for connecting to a secured Elasticsearch cluster.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String username;

    /**
     * The password for connecting to a secured Elasticsearch cluster.
     * References name of a Secret that contains the password. Key 'password' is assumed by default.
     */
    @JsonProperty("password")
    @JsonPropertyDescription("""
            The password for connecting to a secured Elasticsearch cluster.
            References name of a Secret that contains the password. Key `password` is assumed by default.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private SecretKeyRef password;

}
