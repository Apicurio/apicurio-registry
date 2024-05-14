import { ArtifactMetaData } from "@models/artifactMetaData.model.ts";
import { VersionMetaData } from "@models/versionMetaData.model.ts";

export interface CreateArtifactResponse {

    artifact: ArtifactMetaData;
    version?: VersionMetaData;
}
