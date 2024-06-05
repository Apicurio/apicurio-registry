export interface SearchedArtifact {

    groupId: string|null;
    artifactId: string;
    artifactType: string;
    state: string;
    name: string;
    description: string;
    labels: string[];
    createdOn: Date;
    owner: string;
    modifiedOn: Date;
    modifiedBy: string;

}
