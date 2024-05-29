import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { createAuthOptions, createEndpoint, httpGet } from "@utils/rest.utils.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { ArtifactSearchResults } from "@models/artifactSearchResults.model.ts";
import { Paging } from "@models/paging.model.ts";
import { GroupSearchResults } from "@models/groupSearchResults.model.ts";
import { ArtifactSortBy } from "@models/artifactSortBy.model.ts";
import { GroupSortBy } from "@models/groupSortBy.model.ts";
import { SortOrder } from "@models/sortOrder.model.ts";

export enum FilterBy {
    name = "name", description = "description", labels = "labels", groupId = "groupId", artifactId = "artifactId",
    globalId = "globalId", contentId = "contentId"
}

export interface SearchFilter {
    by: FilterBy;
    value: string;
}

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
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/search/artifacts", {}, queryParams);
    const options = await createAuthOptions(auth);
    return httpGet<ArtifactSearchResults>(endpoint, options);
};

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
        queryParams[filter.by] = filter.value;
    });
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/search/groups", {}, queryParams);
    const options = await createAuthOptions(auth);
    return httpGet<GroupSearchResults>(endpoint, options);
};


export interface SearchService {
    searchArtifacts(filters: SearchFilter[], sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults>;
    searchGroups(filters: SearchFilter[], sortBy: GroupSortBy, sortOrder: SortOrder, paging: Paging): Promise<GroupSearchResults>;
}


export const useSearchService: () => SearchService = (): SearchService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        searchArtifacts(filters: SearchFilter[], sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> {
            return searchArtifacts(config, auth, filters, sortBy, sortOrder, paging);
        },
        searchGroups(filters: SearchFilter[], sortBy: GroupSortBy, sortOrder: SortOrder, paging: Paging): Promise<GroupSearchResults> {
            return searchGroups(config, auth, filters, sortBy, sortOrder, paging);
        },
    };
};
