import { SearchedArtifact } from "@models/searchedArtifact.model.ts";

export interface ArtifactSearchResults {
    artifacts: SearchedArtifact[];
    count: number;
}
