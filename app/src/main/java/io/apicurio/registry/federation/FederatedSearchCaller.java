package io.apicurio.registry.federation;

/**
 * What a federated search needs to know about the caller it runs for.
 *
 * @param key tells one caller from another, for the limit on the searches a caller can have in
 *            progress
 * @param seesPeerDetails whether the caller may see which peer registry is which and why one
 *            failed. Only a caller who may list the peers can.
 */
public record FederatedSearchCaller(String key, boolean seesPeerDetails) {
}
