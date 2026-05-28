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
@JsonPropertyOrder({ "url", "branch", "dir" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GitOpsRepoSpec {

    @JsonProperty("url")
    @JsonPropertyDescription("""
            URL of the remote Git repository to sync from. \
            Supports HTTPS and SSH URLs (e.g., `https://github.com/my-org/my-schemas.git`).""")
    @JsonSetter(nulls = SKIP)
    private String url;

    @JsonProperty("branch")
    @JsonPropertyDescription("""
            Branch to track in the remote Git repository. Defaults to the repository's default branch.""")
    @JsonSetter(nulls = SKIP)
    private String branch;

    @JsonProperty("dir")
    @JsonPropertyDescription("""
            Directory name under the workspace where this repository is cloned. \
            Defaults to `default` for single-repo setups. Required when configuring multiple repos \
            to distinguish their clones.""")
    @JsonSetter(nulls = SKIP)
    private String dir;
}
