import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { Paging } from "@models/Paging.ts";
import {
    ArtifactSearchResults,
    ArtifactSortBy,
    GroupSearchResults,
    GroupSortBy,
    SortOrder,
    VersionSearchResults,
    VersionSortBy
} from "@sdk/lib/generated-client/models";

export enum FilterBy {
    name = "name", description = "description", labels = "labels", groupId = "groupId", artifactId = "artifactId",
    globalId = "globalId", contentId = "contentId", version = "version", artifactType = "artifactType", state = "state"
}

export interface SearchFilter {
    by: FilterBy;
    value: string;
}

const searchGroups = async (config: ConfigService, auth: AuthService, filters: SearchFilter[], sortBy: GroupSortBy, sortOrder: SortOrder, paging: Paging): Promise<GroupSearchResults> => {
    console.debug("[SearchService] Searching groups: ", filters, paging);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: sortOrder,
        orderby: sortBy
    };
    filters.forEach(filter => {
        if (filter.value) {
            queryParams[filter.by] = filter.value;
        }
    });

    return getRegistryClient(config, auth).search.groups.get({
        queryParameters: queryParams
    }).then(v => v!);
};

const searchArtifacts = async (config: ConfigService, auth: AuthService, filters: SearchFilter[], sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> => {
    console.debug("[SearchService] Searching artifacts: ", filters, sortBy, sortOrder, paging);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: sortOrder,
        orderby: sortBy
    };
    filters?.forEach(filter => {
        queryParams[filter.by] = filter.value;
    });

    return getRegistryClient(config, auth).search.artifacts.get({
        queryParameters: queryParams
    }).then(v => v!);
};

const searchVersions = async (config: ConfigService, auth: AuthService, filters: SearchFilter[], sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<VersionSearchResults> => {
    console.debug("[SearchService] Searching versions: ", filters, sortBy, sortOrder, paging);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: sortOrder,
        orderby: sortBy
    };
    filters?.forEach(filter => {
        queryParams[filter.by] = filter.value;
    });

    return getRegistryClient(config, auth).search.versions.get({
        queryParameters: queryParams
    }).then(v => v!);
};


export interface SearchService {
    searchGroups(filters: SearchFilter[], sortBy: GroupSortBy, sortOrder: SortOrder, paging: Paging): Promise<GroupSearchResults>;
    searchArtifacts(filters: SearchFilter[], sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults>;
    searchVersions(filters: SearchFilter[], sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<VersionSearchResults>;
}


export const useSearchService: () => SearchService = (): SearchService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        searchGroups(filters: SearchFilter[], sortBy: GroupSortBy, sortOrder: SortOrder, paging: Paging): Promise<GroupSearchResults> {
            return searchGroups(config, auth, filters, sortBy, sortOrder, paging);
        },
        searchArtifacts(filters: SearchFilter[], sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> {
            return searchArtifacts(config, auth, filters, sortBy, sortOrder, paging);
        },
        searchVersions(filters: SearchFilter[], sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> {
            return searchVersions(config, auth, filters, sortBy, sortOrder, paging);
        },
    };
};
