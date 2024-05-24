
export interface EditableArtifactMetaData {
    name?: string;
    description?: string;
    labels?: { [key: string]: string|undefined };
    owner?: string;
}
