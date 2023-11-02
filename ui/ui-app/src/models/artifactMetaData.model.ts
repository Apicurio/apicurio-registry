
export interface ArtifactMetaData {

    groupId: string|null;
    id: string;
    name: string|null;
    description: string|null;
    labels: string[]|null;
    properties: { [key: string]: string };
    type: string;
    version: string;
    createdBy: string;
    createdOn: string;
    modifiedBy: string;
    modifiedOn: string;
    globalId: number;
    contentId: number|null;
    state: string;

}
