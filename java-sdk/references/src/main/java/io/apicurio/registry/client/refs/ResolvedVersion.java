package io.apicurio.registry.client.refs;

/**
 * The version (and draft flag) to use when registering an artifact.
 *
 * @param version the version, or {@code null} to let the registry assign one
 * @param draft whether to create a draft version, or {@code null} for the server default
 * @param fromApiInfo true when the version was taken from the document's {@code info.version}
 * @param fromSnapshot true when a {@code -SNAPSHOT} suffix was stripped from the version
 */
public record ResolvedVersion(String version, Boolean draft, boolean fromApiInfo, boolean fromSnapshot) {
}
