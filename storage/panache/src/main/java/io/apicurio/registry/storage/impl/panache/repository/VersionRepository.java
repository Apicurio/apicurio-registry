package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SearchedArtifact;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.storage.impl.panache.SqlUtil;
import io.apicurio.registry.storage.impl.panache.entity.Artifact;
import io.apicurio.registry.storage.impl.panache.entity.Content;
import io.apicurio.registry.storage.impl.panache.entity.Version;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.StringUtil;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;

import javax.enterprise.context.ApplicationScoped;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class VersionRepository implements PanacheRepository<Version> {

    public Long fetchMaxVersion(String artifactId) {

        return find("artifactId = ?1 where version = max(version)", artifactId)
                .firstResult()
                .version;
    }

    public Version createVersion(boolean firstVersion, Artifact artifact, String name, String description, ArtifactState state,
                                 String createdBy, Date createdOn, String labelsStr, String propertiesStr, Content content) {

        final Version version = new Version();

        version.name = name;
        version.description = description;
        version.state = state.name();
        version.createdBy = createdBy;
        version.createdOn = Timestamp.from(createdOn.toInstant());
        version.labelsStr = labelsStr;
        version.propertiesStr = propertiesStr;

        version.artifact = artifact;
        version.content = content;

        if (firstVersion) {
            version.version = 1L;
        } else {
            version.version = fetchMaxVersion(artifact.artifactId);
        }

        persist(version);

        return version;
    }

    public Version getArtifactLatestVersion(String artifactId) {
        return find("globalId = (select a.latest from Artifact a where a.artifactId = ?1)", artifactId)
                .firstResult();
    }

    public Version getVersion(String artifactId, Long version) {
        return find("artifactId = ?1 and version = ?2", artifactId, version)
                .firstResult();
    }

    public List<Long> getArtifactVersions(String artifactId) {
        return list("artifactId = ?1", artifactId)
                .stream()
                .map(version -> version.globalId)
                .collect(Collectors.toList());
    }

    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver, SortOrder sortOrder) {

        if (!StringUtil.isEmpty(search)) {
            switch (searchOver) {
                case description:
                    return findByDescription(search, offset, limit);
                case everything:
                    return searchEverything(search, offset, limit);
                case labels:
                    return findByLabels(search, offset, limit);
                case name:
                    return findByName(search, offset, limit);
                default:
                    throw new IllegalStateException("No valid search over value");
            }
        } else {
            final PanacheQuery<Version> findAll = find("from Version v where v.globalId IN (select a.latest from Artifact a)")
                    .range(offset, limit - 1);

            return buildSearchResult(findAll.list(), Long.valueOf(findAll.count()).intValue());
        }

    }

    private ArtifactSearchResults findByName(String search, int offset, int limit) {

        final PanacheQuery<Version> nameSearch = find("from Version v where v.name like :nameSearch and v.globalId IN (select a.latest from Artifact a)", Parameters.with("nameSearch", "%" + search + "%"))
                .range(offset, limit - 1);


        return buildSearchResult(nameSearch.list(), Long.valueOf(nameSearch.count()).intValue());
    }

    private ArtifactSearchResults findByDescription(String search, int offset, int limit) {

        final PanacheQuery<Version> descriptionSearch = find("from Version v where v.description like :descriptionSearch and v.globalId IN (select a.latest from Artifact a)", Parameters.with("descriptionSearch", "%" + search + "%"))
                .range(offset, limit - 1);

        return buildSearchResult(descriptionSearch.list(), Long.valueOf(descriptionSearch.count()).intValue());
    }

    private ArtifactSearchResults findByLabels(String search, int offset, int limit) {

        final PanacheQuery<Version> labelSearch = find("from Version v where v.labelsStr like :labelSearch and v.globalId IN (select a.latest from Artifact a)", Parameters.with("labelSearch", "%" + search + "%"))
                .range(offset, limit - 1);

        return buildSearchResult(labelSearch.list(), Long.valueOf(labelSearch.count()).intValue());
    }

    private ArtifactSearchResults searchEverything(String search, int offset, int limit) {

        final Parameters searchParams = Parameters.with("nameSearch", "%" + search + "%")
                .and("descriptionSearch", "%" + search + "%")
                .and("labelSearch",  "%" + search + "%");

        final PanacheQuery<Version> matchedVersions = find(VersionQueries.searchEverything, searchParams)
                .range(offset, limit - 1);

        return buildSearchResult(matchedVersions.list(), Long.valueOf(count(VersionQueries.searchEverythingCount, searchParams)).intValue());
    }

    private ArtifactSearchResults buildSearchResult(List<Version> matchedVersions, int count) {

        final List<SearchedArtifact> searchedArtifacts = buildFromResult(matchedVersions);

        final ArtifactSearchResults artifactSearchResults = new ArtifactSearchResults();
        artifactSearchResults.setCount(count);
        artifactSearchResults.setArtifacts(searchedArtifacts);

        return artifactSearchResults;
    }

    private List<SearchedArtifact> buildFromResult(List<Version> matchedVersions) {

        return matchedVersions.stream()
                .map(this::buildFromVersion)
                .collect(Collectors.toList());
    }

    private SearchedArtifact buildFromVersion(Version version) {

        final SearchedArtifact searchedArtifact = new SearchedArtifact();
        searchedArtifact.setCreatedBy(version.artifact.createdBy);
        searchedArtifact.setCreatedOn(version.artifact.createdOn.getTime());
        searchedArtifact.setDescription(version.description);
        searchedArtifact.setId(version.artifact.artifactId);
        searchedArtifact.setLabels(SqlUtil.deserializeLabels(version.labelsStr));
        searchedArtifact.setModifiedBy(version.createdBy);
        searchedArtifact.setModifiedOn(version.createdOn.getTime());
        searchedArtifact.setState(ArtifactState.fromValue(version.state));
        searchedArtifact.setType(ArtifactType.fromValue(version.artifact.artifactType));
        searchedArtifact.setName(version.name);
        return searchedArtifact;
    }


    private static class VersionQueries {

        protected static final String searchEverything = "from Version v where (v.name like :nameSearch" +
                " OR v.description like :descriptionSearch" +
                " OR v.labelsStr like :labelSearch" +
                ")" +
                " AND v.globalId IN (select a.latest from Artifact a)" +
                " group by v.artifact" +
                " order by(COALESCE(v.artifact, v.name))";

        protected static final String searchEverythingCount = "from Version v where (v.name like :nameSearch" +
                " OR v.description like :descriptionSearch" +
                " OR v.labelsStr like :labelSearch" +
                ")" +
                " AND v.globalId IN (select a.latest from Artifact a)";
    }
}
