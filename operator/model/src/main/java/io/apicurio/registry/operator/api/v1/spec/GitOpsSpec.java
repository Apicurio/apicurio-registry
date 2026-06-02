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

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "mode", "repos", "registryId", "pull", "push" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class GitOpsSpec {

    @JsonProperty("mode")
    @JsonPropertyDescription("""
            GitOps sync mode. `pull` (default) periodically fetches from remote repositories. \
            `push` starts an SSH server that accepts git push. \
            In push mode, the operator creates an additional service for SSH access on port 2222.""")
    @JsonSetter(nulls = SKIP)
    private GitOpsMode mode;

    @JsonProperty("repos")
    @JsonPropertyDescription("""
            List of Git repositories to sync from. Each entry configures a repository URL, \
            optional branch, and directory name. For single-repo setups, provide a list with one entry.""")
    @JsonSetter(nulls = SKIP)
    private List<GitOpsRepoSpec> repos;

    @JsonProperty("registryId")
    @JsonPropertyDescription("""
            Registry instance identifier. Only data files with a matching registry ID will be loaded. \
            Defaults to `default`.""")
    @JsonSetter(nulls = SKIP)
    private String registryId;

    @JsonProperty("pull")
    @JsonPropertyDescription("""
            Configure SSH secrets for pull mode (authenticating to remote Git servers).""")
    @JsonSetter(nulls = SKIP)
    private GitOpsPullSpec pull;

    @JsonProperty("push")
    @JsonPropertyDescription("""
            Configure SSH secrets for push mode (SSH server accepting pushes).""")
    @JsonSetter(nulls = SKIP)
    private GitOpsPushSpec push;
}
