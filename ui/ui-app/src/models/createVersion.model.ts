import { VersionContent } from "@models/versionContent.model.ts";

export interface CreateVersion {

    version?: string;
    content: VersionContent;
    name?: string;
    description?: string;
    labels?: { [key: string]: string };
    branches?: string[];

}
