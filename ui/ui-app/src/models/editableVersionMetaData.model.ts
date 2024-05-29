import { VersionState } from "@models/versionState.model.ts";

export interface EditableVersionMetaData {
    name?: string;
    description?: string;
    labels?: { [key: string]: string|undefined };
    state?: VersionState;
}
