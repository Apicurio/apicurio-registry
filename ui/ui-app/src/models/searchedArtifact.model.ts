export interface SearchedArtifact {

    groupId: string|null;
    id: string;
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
