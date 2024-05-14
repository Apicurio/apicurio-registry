import { ArtifactReference } from "@models/artifactReference.model.ts";

export interface VersionContent {

    content: string;
    contentType: string;
    references?: ArtifactReference[];

}
