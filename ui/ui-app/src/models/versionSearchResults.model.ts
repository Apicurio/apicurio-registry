import { SearchedVersion } from "@models/searchedVersion.model.ts";

export interface VersionSearchResults {
    versions: SearchedVersion[];
    count: number;
}
