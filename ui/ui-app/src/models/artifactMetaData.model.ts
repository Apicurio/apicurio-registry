
export interface ArtifactMetaData {

    groupId: string|null;
    artifactId: string;
    name: string|null;
    description: string|null;
    labels: { [key: string]: string };
    type: string;
    version: string;
    owner: string;
    createdOn: string;
    modifiedBy: string;
    modifiedOn: string;
    globalId: number;
    contentId: number|null;
    state: string;

}
