export interface SearchedArtifact {

    groupId: string|null;
    artifactId: string;
    type: string;
    state: string;
    name: string;
    description: string;
    labels: string[];
    createdOn: Date;
    owner: string;
    modifiedOn: Date;
    modifiedBy: string;

}
