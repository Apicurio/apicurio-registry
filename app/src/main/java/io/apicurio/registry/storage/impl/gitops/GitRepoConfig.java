package io.apicurio.registry.storage.impl.gitops;

/**
 * Configuration for a single Git repository in the GitOps storage.
 *
 * @param id unique identifier for this repo (derived from directory name or index)
 * @param dir directory name of the Git repository, relative to the workspace
 * @param branch branch to read from
 */
public record GitRepoConfig(String id, String dir, String branch) {
}
