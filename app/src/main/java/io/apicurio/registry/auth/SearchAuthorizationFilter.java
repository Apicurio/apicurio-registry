package io.apicurio.registry.auth;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * POC: Application-layer authorization filtering for search/list results.
 *
 * Instead of filtering at the SQL level (JOINs against an ACL table), this approach
 * over-fetches from the database and filters results through an authorization check
 * at the application layer.
 *
 * This class exists to measure the overhead of this approach and determine whether
 * it's viable as an alternative to SQL-level filtering.
 */
@ApplicationScoped
public class SearchAuthorizationFilter {

    private static final int DEFAULT_FETCH_MULTIPLIER = 3;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Searches artifacts with application-layer authorization filtering.
     * Over-fetches from the database and filters results through the provided predicate.
     *
     * @param filters   SQL-level search filters (name, labels, etc.)
     * @param orderBy   sort field
     * @param orderDir  sort direction
     * @param offset    desired offset in the filtered result set
     * @param limit     desired page size
     * @param authzCheck predicate that returns true if the artifact is authorized for the current user
     * @return filtered search results with up to `limit` authorized artifacts
     */
    public ArtifactSearchResultsDto searchArtifactsWithAuthz(
            Set<SearchFilter> filters,
            OrderBy orderBy,
            OrderDirection orderDir,
            int offset,
            int limit,
            Predicate<SearchedArtifactDto> authzCheck) {

        int dbOffset = 0;
        int fetchSize = limit * DEFAULT_FETCH_MULTIPLIER;
        int skipped = 0;
        int totalAuthorized = 0;
        List<SearchedArtifactDto> collected = new ArrayList<>();

        while (collected.size() < limit) {
            ArtifactSearchResultsDto batch = storage.searchArtifacts(
                    filters, orderBy, orderDir, dbOffset, fetchSize);

            if (batch.getArtifacts().isEmpty()) {
                break;
            }

            for (SearchedArtifactDto artifact : batch.getArtifacts()) {
                if (authzCheck.test(artifact)) {
                    totalAuthorized++;
                    if (skipped < offset) {
                        skipped++;
                    } else if (collected.size() < limit) {
                        collected.add(artifact);
                    }
                }
            }

            dbOffset += batch.getArtifacts().size();

            // If we got fewer results than requested, we've exhausted the dataset
            if (batch.getArtifacts().size() < fetchSize) {
                break;
            }
        }

        return ArtifactSearchResultsDto.builder()
                .artifacts(collected)
                .count((long) totalAuthorized)
                .build();
    }

    /**
     * Searches groups with application-layer authorization filtering.
     */
    public GroupSearchResultsDto searchGroupsWithAuthz(
            Set<SearchFilter> filters,
            OrderBy orderBy,
            OrderDirection orderDir,
            int offset,
            int limit,
            Predicate<SearchedGroupDto> authzCheck) {

        int dbOffset = 0;
        int fetchSize = limit * DEFAULT_FETCH_MULTIPLIER;
        int skipped = 0;
        int totalAuthorized = 0;
        List<SearchedGroupDto> collected = new ArrayList<>();

        while (collected.size() < limit) {
            GroupSearchResultsDto batch = storage.searchGroups(
                    filters, orderBy, orderDir, dbOffset, fetchSize);

            if (batch.getGroups().isEmpty()) {
                break;
            }

            for (SearchedGroupDto group : batch.getGroups()) {
                if (authzCheck.test(group)) {
                    totalAuthorized++;
                    if (skipped < offset) {
                        skipped++;
                    } else if (collected.size() < limit) {
                        collected.add(group);
                    }
                }
            }

            dbOffset += batch.getGroups().size();

            if (batch.getGroups().size() < fetchSize) {
                break;
            }
        }

        return GroupSearchResultsDto.builder()
                .groups(collected)
                .count(totalAuthorized)
                .build();
    }
}
