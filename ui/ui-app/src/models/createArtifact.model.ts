import { CreateVersion } from "@models/createVersion.model.ts";

export interface CreateArtifact {

    artifactId?: string;
    name?: string;
    description?: string;
    labels?: { [key: string]: string };
    type?: string;
    firstVersion?: CreateVersion;
}
