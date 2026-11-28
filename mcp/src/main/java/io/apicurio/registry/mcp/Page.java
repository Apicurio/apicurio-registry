package io.apicurio.registry.mcp;

import java.util.List;

/**
 * One page of results returned by a paged MCP list or search tool.
 *
 * @param results the items on this page
 * @param count the total number of matches across all pages, as reported by the registry
 * @param pageToken opaque token to pass to the same tool to get the next page, or {@code null}
 *        when this is the last page (the same field name {@code ard_search} uses)
 */
public record Page<T>(List<T> results, Integer count, String pageToken) {
}
